package com.wms.system.platform.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.dto.PlatformAdminMutationResponse;
import com.wms.system.platform.dto.PlatformAdminStatusChallengeResponse;
import com.wms.system.platform.dto.PlatformAdminStatusMutationRequest;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformAdminInvitationRepository;
import com.wms.system.platform.repository.PlatformMfaChallengeRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.platform.repository.PlatformUserRoleRepository;
import com.wms.system.security.PlatformSecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlatformAdminStatusService {
    static final String SUPER_ADMIN = "PLATFORM_SUPER_ADMIN";
    static final String READ_CAPABILITY = "PLATFORM_TENANT_READ";
    static final String EXPORT_CAPABILITY = "PLATFORM_TENANT_EXPORT";
    static final String OPERATIONS_ADMIN = "PLATFORM_OPERATIONS_ADMIN";
    static final String SECURITY_AUDITOR = "PLATFORM_SECURITY_AUDITOR";
    static final String STATUS_CHANGE_PURPOSE = "ADMIN_STATUS_CHANGE";
    static final Set<String> SAFE_ENABLE_ROLES = Set.of(
        SUPER_ADMIN, OPERATIONS_ADMIN, SECURITY_AUDITOR, READ_CAPABILITY, EXPORT_CAPABILITY);

    private final PlatformAccessGuard guard;
    private final PlatformUserRepository users;
    private final PlatformUserRoleRepository userRoles;
    private final PlatformAdminInvitationRepository invitations;
    private final PlatformMfaChallengeRepository challenges;
    private final PlatformMfaCrypto crypto;
    private final PlatformMfaProperties properties;
    private final PlatformAuditService audit;
    private final PlatformAdminStatusMutationExecutor mutationExecutor;

    @Transactional
    public PlatformAdminStatusChallengeResponse startChallenge(Long targetUserId, boolean desiredEnabled,
                                                               HttpServletRequest http) {
        PlatformSecurityUser principal = guard.requireSuperAdmin();
        Long actorId = principal.getId();
        String action = auditAction(desiredEnabled);
        if (targetUserId == null) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", "targetUserId"));
        }
        if (actorId.equals(targetUserId)) {
            audit.recordAdmin(actorId, targetUserId, action, "platform_admin_status", "FAILED",
                Map.of("targetPlatformUserId", targetUserId, "errorKey", ErrorKeys.AUTH_ACCESS_DENIED), http);
            throw new AccessDeniedException("Platform administrators cannot change their own account status");
        }

        PlatformUser actor = users.findById(actorId)
            .filter(candidate -> Boolean.TRUE.equals(candidate.getEnabled()))
            .orElseThrow(() -> new BusinessException(ErrorKeys.AUTH_ACCESS_DENIED));
        ensureActorReady(actor);
        PlatformUser target = users.findById(targetUserId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.PLATFORM_ADMIN_NOT_FOUND,
                Map.of("targetUserId", targetUserId)));
        List<String> roles = userRoles.findRoleCodesByPlatformUserId(targetUserId);
        validateRequestedTransition(target, roles, desiredEnabled);

        String rawToken = crypto.newChallengeToken();
        PlatformMfaChallenge challenge = challenges.saveAndFlush(PlatformMfaChallenge.builder()
            .tokenHash(crypto.hashToken(rawToken))
            .platformUserId(actorId)
            .targetPlatformUserId(targetUserId)
            .purpose(STATUS_CHANGE_PURPOSE)
            .actionContextHash(contextHash(crypto, targetUserId, desiredEnabled))
            .targetSecurityVersion(target.getSecurityVersion())
            .expiresAt(OffsetDateTime.now().plusMinutes(properties.getChallengeMinutes()))
            .build());
        audit.recordAdmin(actorId, targetUserId, action, "platform_admin_status", "REQUESTED",
            Map.of(
                "targetPlatformUserId", targetUserId,
                "desiredEnabled", desiredEnabled,
                "targetSecurityVersion", target.getSecurityVersion(),
                "expiresAt", challenge.getExpiresAt().toString()
            ), http);
        return new PlatformAdminStatusChallengeResponse(
            rawToken,
            properties.getChallengeMinutes() * 60_000L,
            target.getSecurityVersion()
        );
    }

    public PlatformAdminMutationResponse changeStatus(
        Long targetUserId,
        boolean desiredEnabled,
        String idempotencyKey,
        PlatformAdminStatusMutationRequest request,
        HttpServletRequest http
    ) {
        PlatformSecurityUser principal = guard.requireSuperAdmin();
        Long actorId = principal.getId();
        String action = auditAction(desiredEnabled);
        String safeKey = normalizeIdempotencyKey(idempotencyKey);
        String reason = normalizeReason(request == null ? null : request.getReason());
        if (targetUserId == null) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", "targetUserId"));
        }
        if (actorId.equals(targetUserId)) {
            audit.recordAdmin(actorId, targetUserId, action, "platform_admin_status", "FAILED",
                failureDetail(targetUserId, reason, safeKey, ErrorKeys.AUTH_ACCESS_DENIED), http);
            throw new AccessDeniedException("Platform administrators cannot change their own account status");
        }
        if (!users.existsById(targetUserId)) {
            audit.record(actorId, null, action, "platform_admin_status", "FAILED",
                failureDetail(targetUserId, reason, safeKey, ErrorKeys.PLATFORM_ADMIN_NOT_FOUND), http);
            throw new BusinessException(ErrorKeys.PLATFORM_ADMIN_NOT_FOUND,
                Map.of("targetUserId", targetUserId));
        }

        try {
            return mutationExecutor.execute(
                actorId, targetUserId, desiredEnabled, safeKey, reason, request, http);
        } catch (BusinessException exception) {
            audit.recordAdmin(actorId, targetUserId, action, "platform_admin_status", "FAILED",
                failureDetail(targetUserId, reason, safeKey, exception.getErrorKey()), http);
            if (ErrorKeys.AUTH_FAILED.equals(exception.getErrorKey())) {
                audit.record(actorId, null, "MFA_FAILED", "platform_identity", "FAILED",
                    Map.of("operation", action, "targetPlatformUserId", targetUserId), http);
            }
            throw exception;
        }
    }

    private void validateRequestedTransition(PlatformUser target, List<String> roles, boolean desiredEnabled) {
        boolean currentlyEnabled = Boolean.TRUE.equals(target.getEnabled());
        if (currentlyEnabled == desiredEnabled) return;
        boolean superAdmin = roles.contains(SUPER_ADMIN);
        if (!desiredEnabled && superAdmin && userRoles.countEnabledUsersByRoleCode(SUPER_ADMIN) <= 1) {
            throw new BusinessException(ErrorKeys.PLATFORM_ADMIN_LAST_SUPER_ADMIN,
                Map.of("targetUserId", target.getId()));
        }
        if (desiredEnabled) {
            requireSafeEnableRoles(target.getId(), roles);
            if (superAdmin) {
                long enabled = userRoles.countEnabledUsersByRoleCode(SUPER_ADMIN);
                long pending = invitations.countActiveByRoleCode(SUPER_ADMIN, OffsetDateTime.now());
                if (enabled + pending >= 2) {
                    throw new BusinessException(ErrorKeys.PLATFORM_ADMIN_SUPER_ADMIN_LIMIT,
                        Map.of("maximumActiveSeats", 2));
                }
            }
        }
    }

    static void requireSafeEnableRoles(Long targetUserId, List<String> roles) {
        if (roles == null || roles.isEmpty() || roles.stream().anyMatch(role -> !SAFE_ENABLE_ROLES.contains(role))) {
            throw new BusinessException(ErrorKeys.PLATFORM_ADMIN_ENABLE_REVIEW_REQUIRED,
                Map.of("targetUserId", targetUserId));
        }
    }

    private void ensureActorReady(PlatformUser actor) {
        OffsetDateTime now = OffsetDateTime.now();
        if (actor.getMfaLockedUntil() != null && now.isBefore(actor.getMfaLockedUntil())) {
            throw new BusinessException(ErrorKeys.AUTH_FAILED,
                Map.of("reason", "MFA_VERIFICATION_FAILED", "mfaReason", "TEMPORARILY_LOCKED"));
        }
        if (actor.getMfaLockedUntil() != null) {
            actor.setMfaFailedAttempts(0);
            actor.setMfaLockedUntil(null);
            users.save(actor);
        }
        if (!Boolean.TRUE.equals(actor.getMfaEnabled()) || actor.getMfaSecretEncrypted() == null) {
            throw new BusinessException(ErrorKeys.AUTH_FAILED,
                Map.of("reason", "MFA_VERIFICATION_FAILED"));
        }
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", "Idempotency-Key"));
        }
        String normalized = value.trim();
        if (normalized.length() > 80) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "Idempotency-Key", "maxLength", 80));
        }
        return normalized;
    }

    private String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", "reason"));
        }
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "reason", "maxLength", 500));
        }
        return normalized;
    }

    private Map<String, Object> failureDetail(Long targetUserId, String reason, String key, String errorKey) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetPlatformUserId", targetUserId);
        detail.put("reason", reason);
        detail.put("errorKey", errorKey);
        detail.put("idempotencyKeyHash", crypto.hashToken(key));
        return detail;
    }

    static String contextHash(PlatformMfaCrypto crypto, Long targetUserId, boolean desiredEnabled) {
        return crypto.hashToken("ADMIN_STATUS_CHANGE\n" + targetUserId + "\n" + desiredEnabled);
    }

    static String auditAction(boolean desiredEnabled) {
        return desiredEnabled ? "ADMIN_ENABLED" : "ADMIN_DISABLED";
    }
}

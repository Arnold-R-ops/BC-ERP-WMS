package com.wms.system.platform.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.dto.PlatformAdminRoleChangeMutationRequest;
import com.wms.system.platform.dto.PlatformAdminRoleChangeResponse;
import com.wms.system.platform.dto.PlatformAdminStatusChallengeResponse;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformAccessGrantRepository;
import com.wms.system.platform.repository.PlatformMfaChallengeRepository;
import com.wms.system.platform.repository.PlatformRoleRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.platform.repository.PlatformUserRoleRepository;
import com.wms.system.security.PlatformSecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlatformAdminRoleService {
    static final String ROLE_CHANGE_PURPOSE = "ADMIN_ROLES_CHANGE";
    static final String ROLE_CHANGE_COMMAND = "ADMIN_ROLES_CHANGE";
    static final String ROLE_CHANGE_AUDIT = "ADMIN_ROLES_CHANGED";
    static final String OPERATIONS_ADMIN = "PLATFORM_OPERATIONS_ADMIN";
    static final String SECURITY_AUDITOR = "PLATFORM_SECURITY_AUDITOR";
    private static final Set<String> ORDINARY_JOB_ROLES = Set.of(
        OPERATIONS_ADMIN, SECURITY_AUDITOR);
    private static final Set<String> MIGRATABLE_ORDINARY_ROLES = Set.of(
        OPERATIONS_ADMIN,
        SECURITY_AUDITOR,
        PlatformAdminStatusService.READ_CAPABILITY,
        PlatformAdminStatusService.EXPORT_CAPABILITY
    );

    private final PlatformAccessGuard guard;
    private final PlatformUserRepository users;
    private final PlatformUserRoleRepository userRoles;
    private final PlatformRoleRepository roles;
    private final PlatformAccessGrantRepository accessGrants;
    private final PlatformMfaChallengeRepository challenges;
    private final PlatformMfaCrypto crypto;
    private final PlatformMfaProperties properties;
    private final PlatformAuditService audit;
    private final PlatformAdminRoleMutationExecutor mutationExecutor;

    @Transactional
    public PlatformAdminStatusChallengeResponse startChallenge(
        Long targetUserId,
        String desiredRole,
        HttpServletRequest http
    ) {
        PlatformSecurityUser principal = guard.requireSuperAdmin();
        Long actorId = principal.getId();
        validateTargetId(actorId, targetUserId);
        validateDesiredRole(desiredRole);

        PlatformUser actor = users.findById(actorId)
            .filter(candidate -> Boolean.TRUE.equals(candidate.getEnabled()))
            .orElseThrow(() -> new BusinessException(ErrorKeys.AUTH_ACCESS_DENIED));
        ensureActorReady(actor);
        PlatformUser target = users.findById(targetUserId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PLATFORM_ADMIN_NOT_FOUND, Map.of("targetUserId", targetUserId)));
        List<String> beforeRoles = userRoles.findRoleCodesByPlatformUserId(targetUserId);
        validateTargetRole(targetUserId, beforeRoles, desiredRole, OffsetDateTime.now());
        roles.findByRoleCode(desiredRole)
            .orElseThrow(() -> new IllegalStateException("Required platform role is missing"));

        String rawToken = crypto.newChallengeToken();
        PlatformMfaChallenge challenge = challenges.saveAndFlush(PlatformMfaChallenge.builder()
            .tokenHash(crypto.hashToken(rawToken))
            .platformUserId(actorId)
            .targetPlatformUserId(targetUserId)
            .purpose(ROLE_CHANGE_PURPOSE)
            .actionContextHash(contextHash(crypto, targetUserId, desiredRole))
            .targetSecurityVersion(target.getSecurityVersion())
            .expiresAt(OffsetDateTime.now().plusMinutes(properties.getChallengeMinutes()))
            .build());
        audit.recordAdmin(actorId, targetUserId, ROLE_CHANGE_AUDIT, "platform_admin_roles", "REQUESTED",
            Map.of(
                "targetPlatformUserId", targetUserId,
                "beforeRoles", beforeRoles,
                "desiredRole", desiredRole,
                "targetEnabled", Boolean.TRUE.equals(target.getEnabled()),
                "targetSecurityVersion", target.getSecurityVersion(),
                "expiresAt", challenge.getExpiresAt().toString()
            ), http);
        return new PlatformAdminStatusChallengeResponse(
            rawToken,
            properties.getChallengeMinutes() * 60_000L,
            target.getSecurityVersion()
        );
    }

    public PlatformAdminRoleChangeResponse changeRole(
        Long targetUserId,
        String idempotencyKey,
        PlatformAdminRoleChangeMutationRequest request,
        HttpServletRequest http
    ) {
        PlatformSecurityUser principal = guard.requireSuperAdmin();
        Long actorId = principal.getId();
        validateTargetId(actorId, targetUserId);
        String desiredRole = request == null ? null : request.getJobRoleCode();
        validateDesiredRole(desiredRole);
        String safeKey = normalizeIdempotencyKey(idempotencyKey);
        String reason = normalizeReason(request.getReason());
        try {
            return mutationExecutor.execute(
                actorId, targetUserId, desiredRole, safeKey, reason, request, http);
        } catch (BusinessException exception) {
            audit.recordAdmin(actorId, targetUserId, ROLE_CHANGE_AUDIT, "platform_admin_roles", "FAILED",
                Map.of(
                    "targetPlatformUserId", targetUserId,
                    "desiredRole", desiredRole,
                    "errorKey", exception.getErrorKey(),
                    "idempotencyKeyHash", crypto.hashToken(safeKey)
                ), http);
            if (ErrorKeys.AUTH_FAILED.equals(exception.getErrorKey())) {
                audit.record(actorId, null, "MFA_FAILED", "platform_identity", "FAILED",
                    Map.of("operation", ROLE_CHANGE_COMMAND, "targetPlatformUserId", targetUserId), http);
            }
            throw exception;
        }
    }

    static void validateOrdinaryRoleSet(Long targetUserId, List<String> currentRoles) {
        if (currentRoles == null || currentRoles.isEmpty()
            || currentRoles.stream().anyMatch(role -> !MIGRATABLE_ORDINARY_ROLES.contains(role))) {
            throw new BusinessException(
                ErrorKeys.PLATFORM_ADMIN_ROLE_CHANGE_FORBIDDEN,
                Map.of("targetUserId", targetUserId)
            );
        }
    }

    void validateTargetRole(
        Long targetUserId,
        List<String> currentRoles,
        String desiredRole,
        OffsetDateTime now
    ) {
        validateOrdinaryRoleSet(targetUserId, currentRoles);
        if (SECURITY_AUDITOR.equals(desiredRole)) {
            long blockingGrants = accessGrants.countCurrentOrFutureByPlatformUserId(targetUserId, now);
            if (blockingGrants > 0) {
                throw new BusinessException(
                    ErrorKeys.PLATFORM_ADMIN_ACTIVE_GRANTS_EXIST,
                    Map.of("targetUserId", targetUserId, "blockingGrantCount", blockingGrants)
                );
            }
        }
    }

    private void validateTargetId(Long actorId, Long targetUserId) {
        if (targetUserId == null) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", "targetUserId"));
        }
        if (actorId.equals(targetUserId)) {
            throw new AccessDeniedException("Platform administrators cannot change their own job role");
        }
    }

    private void validateDesiredRole(String role) {
        if (!ORDINARY_JOB_ROLES.contains(role)) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", "jobRoleCode"));
        }
    }

    private void ensureActorReady(PlatformUser actor) {
        OffsetDateTime now = OffsetDateTime.now();
        if (actor.getMfaLockedUntil() != null && now.isBefore(actor.getMfaLockedUntil())) {
            throw invalidMfa("TEMPORARILY_LOCKED");
        }
        if (actor.getMfaLockedUntil() != null) {
            actor.setMfaFailedAttempts(0);
            actor.setMfaLockedUntil(null);
            users.save(actor);
        }
        if (!Boolean.TRUE.equals(actor.getMfaEnabled()) || actor.getMfaSecretEncrypted() == null) {
            throw invalidMfa("MFA_NOT_ENROLLED");
        }
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 80) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", "Idempotency-Key"));
        }
        return value.trim();
    }

    private String normalizeReason(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 500) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", "reason"));
        }
        return value.trim();
    }

    static String contextHash(PlatformMfaCrypto crypto, Long targetUserId, String desiredRole) {
        return crypto.hashToken(ROLE_CHANGE_COMMAND + "\n" + targetUserId + "\n" + desiredRole);
    }

    private BusinessException invalidMfa(String reason) {
        return new BusinessException(
            ErrorKeys.AUTH_FAILED,
            Map.of("reason", "MFA_VERIFICATION_FAILED", "mfaReason", reason)
        );
    }
}

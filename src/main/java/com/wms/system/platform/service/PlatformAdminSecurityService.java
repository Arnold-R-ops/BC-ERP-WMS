package com.wms.system.platform.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.dto.PlatformAdminMfaResetRequest;
import com.wms.system.platform.dto.PlatformAdminSecurityChallengeResponse;
import com.wms.system.platform.dto.PlatformAdminSecurityMutationResponse;
import com.wms.system.platform.dto.PlatformAdminSessionRevokeRequest;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformMfaChallengeRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.security.PlatformSecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformAdminSecurityService {
    private final PlatformAccessGuard guard;
    private final PlatformUserRepository users;
    private final PlatformMfaChallengeRepository challenges;
    private final PlatformMfaCrypto crypto;
    private final PlatformMfaProperties properties;
    private final PlatformAuditService audit;
    private final PlatformAdminSecurityMutationExecutor mutationExecutor;

    @Transactional
    public PlatformAdminSecurityChallengeResponse startSessionRevoke(
        Long targetUserId,
        HttpServletRequest http
    ) {
        return startChallenge(targetUserId, PlatformAdminSecurityOperation.SESSION_REVOKE, http);
    }

    @Transactional
    public PlatformAdminSecurityChallengeResponse startMfaReset(
        Long targetUserId,
        HttpServletRequest http
    ) {
        return startChallenge(targetUserId, PlatformAdminSecurityOperation.MFA_RESET, http);
    }

    public PlatformAdminSecurityMutationResponse revokeSessions(
        Long targetUserId,
        String idempotencyKey,
        PlatformAdminSessionRevokeRequest request,
        HttpServletRequest http
    ) {
        return execute(
            targetUserId,
            PlatformAdminSecurityOperation.SESSION_REVOKE,
            idempotencyKey,
            request == null ? null : request.getChallengeToken(),
            request == null ? null : request.getPassword(),
            request == null ? null : request.getCode(),
            request == null ? null : request.getReason(),
            http
        );
    }

    public PlatformAdminSecurityMutationResponse resetMfa(
        Long targetUserId,
        String idempotencyKey,
        PlatformAdminMfaResetRequest request,
        HttpServletRequest http
    ) {
        return execute(
            targetUserId,
            PlatformAdminSecurityOperation.MFA_RESET,
            idempotencyKey,
            request == null ? null : request.getChallengeToken(),
            request == null ? null : request.getPassword(),
            request == null ? null : request.getCode(),
            request == null ? null : request.getReason(),
            http
        );
    }

    private PlatformAdminSecurityChallengeResponse startChallenge(
        Long targetUserId,
        PlatformAdminSecurityOperation operation,
        HttpServletRequest http
    ) {
        PlatformSecurityUser principal = guard.requireSuperAdmin();
        Long actorId = principal.getId();
        validateTargetId(actorId, targetUserId, operation, http);

        try {
            PlatformUser actor = users.findById(actorId)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getEnabled()))
                .orElseThrow(() -> new BusinessException(ErrorKeys.AUTH_ACCESS_DENIED));
            ensureActorReady(actor);
            PlatformUser target = users.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.PLATFORM_ADMIN_NOT_FOUND,
                    Map.of("targetUserId", targetUserId)
                ));
            validateTarget(operation, target);

            String rawToken = crypto.newChallengeToken();
            PlatformMfaChallenge challenge = challenges.saveAndFlush(PlatformMfaChallenge.builder()
                .tokenHash(crypto.hashToken(rawToken))
                .platformUserId(actorId)
                .targetPlatformUserId(targetUserId)
                .purpose(operation.purpose())
                .actionContextHash(contextHash(crypto, operation, targetUserId))
                .targetSecurityVersion(target.getSecurityVersion())
                .expiresAt(OffsetDateTime.now().plusMinutes(properties.getChallengeMinutes()))
                .build());
            audit.recordAdmin(
                actorId,
                targetUserId,
                operation.auditAction(),
                operation.resourceType(),
                "REQUESTED",
                Map.of(
                    "targetPlatformUserId", targetUserId,
                    "targetSecurityVersion", target.getSecurityVersion(),
                    "expiresAt", challenge.getExpiresAt().toString()
                ),
                http
            );
            return new PlatformAdminSecurityChallengeResponse(
                rawToken,
                properties.getChallengeMinutes() * 60_000L,
                target.getSecurityVersion()
            );
        } catch (BusinessException exception) {
            recordChallengeFailure(actorId, targetUserId, operation, exception.getErrorKey(), http);
            throw exception;
        }
    }

    private PlatformAdminSecurityMutationResponse execute(
        Long targetUserId,
        PlatformAdminSecurityOperation operation,
        String idempotencyKey,
        String challengeToken,
        String password,
        String code,
        String rawReason,
        HttpServletRequest http
    ) {
        PlatformSecurityUser principal = guard.requireSuperAdmin();
        Long actorId = principal.getId();
        String safeKey = normalizeIdempotencyKey(idempotencyKey);
        String reason = normalizeReason(rawReason);
        validateTargetId(actorId, targetUserId, operation, http);
        if (!users.existsById(targetUserId)) {
            audit.record(
                actorId,
                null,
                operation.auditAction(),
                operation.resourceType(),
                "FAILED",
                failureDetail(targetUserId, reason, safeKey, ErrorKeys.PLATFORM_ADMIN_NOT_FOUND),
                http
            );
            throw new BusinessException(
                ErrorKeys.PLATFORM_ADMIN_NOT_FOUND,
                Map.of("targetUserId", targetUserId)
            );
        }

        try {
            return mutationExecutor.execute(
                actorId,
                targetUserId,
                operation,
                safeKey,
                reason,
                challengeToken,
                password,
                code,
                http
            );
        } catch (BusinessException exception) {
            if (ErrorKeys.PLATFORM_ADMIN_NOT_FOUND.equals(exception.getErrorKey())) {
                audit.record(
                    actorId,
                    null,
                    operation.auditAction(),
                    operation.resourceType(),
                    "FAILED",
                    failureDetail(targetUserId, reason, safeKey, exception.getErrorKey()),
                    http
                );
            } else {
                audit.recordAdmin(
                    actorId,
                    targetUserId,
                    operation.auditAction(),
                    operation.resourceType(),
                    "FAILED",
                    failureDetail(targetUserId, reason, safeKey, exception.getErrorKey()),
                    http
                );
            }
            if (ErrorKeys.AUTH_FAILED.equals(exception.getErrorKey())) {
                audit.record(
                    actorId,
                    null,
                    "MFA_FAILED",
                    "platform_identity",
                    "FAILED",
                    Map.of(
                        "operation", operation.commandAction(),
                        "targetPlatformUserId", targetUserId
                    ),
                    http
                );
            }
            throw exception;
        }
    }

    private void validateTargetId(
        Long actorId,
        Long targetUserId,
        PlatformAdminSecurityOperation operation,
        HttpServletRequest http
    ) {
        if (targetUserId == null) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", "targetUserId"));
        }
        if (actorId.equals(targetUserId)) {
            audit.recordAdmin(
                actorId,
                targetUserId,
                operation.auditAction(),
                operation.resourceType(),
                "FAILED",
                Map.of("targetPlatformUserId", targetUserId, "errorKey", ErrorKeys.AUTH_ACCESS_DENIED),
                http
            );
            throw new AccessDeniedException("Platform administrators cannot perform this operation on themselves");
        }
    }

    private void validateTarget(PlatformAdminSecurityOperation operation, PlatformUser target) {
        if (!Boolean.TRUE.equals(target.getEnabled())) {
            throw new BusinessException(
                ErrorKeys.PLATFORM_ADMIN_DISABLED,
                Map.of("targetUserId", target.getId())
            );
        }
        if (operation == PlatformAdminSecurityOperation.MFA_RESET
            && (!Boolean.TRUE.equals(target.getMfaEnabled()) || target.getMfaSecretEncrypted() == null)) {
            throw new BusinessException(
                ErrorKeys.PLATFORM_ADMIN_MFA_NOT_ENROLLED,
                Map.of("targetUserId", target.getId())
            );
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
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", "Idempotency-Key"));
        }
        String normalized = value.trim();
        if (normalized.length() > 80) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "Idempotency-Key", "maxLength", 80)
            );
        }
        return normalized;
    }

    private String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", "reason"));
        }
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "reason", "maxLength", 500)
            );
        }
        return normalized;
    }

    private void recordChallengeFailure(
        Long actorId,
        Long targetUserId,
        PlatformAdminSecurityOperation operation,
        String errorKey,
        HttpServletRequest http
    ) {
        if (ErrorKeys.PLATFORM_ADMIN_NOT_FOUND.equals(errorKey)) {
            audit.record(
                actorId,
                null,
                operation.auditAction(),
                operation.resourceType(),
                "FAILED",
                Map.of("targetPlatformUserId", targetUserId, "errorKey", errorKey),
                http
            );
            return;
        }
        audit.recordAdmin(
            actorId,
            targetUserId,
            operation.auditAction(),
            operation.resourceType(),
            "FAILED",
            Map.of("targetPlatformUserId", targetUserId, "errorKey", errorKey),
            http
        );
    }

    private Map<String, Object> failureDetail(
        Long targetUserId,
        String reason,
        String idempotencyKey,
        String errorKey
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetPlatformUserId", targetUserId);
        detail.put("reason", reason);
        detail.put("errorKey", errorKey);
        detail.put("idempotencyKeyHash", crypto.hashToken(idempotencyKey));
        return detail;
    }

    static String contextHash(
        PlatformMfaCrypto crypto,
        PlatformAdminSecurityOperation operation,
        Long targetUserId
    ) {
        return crypto.hashToken(operation.commandAction() + "\n" + targetUserId);
    }

    private BusinessException invalidMfa(String mfaReason) {
        return new BusinessException(
            ErrorKeys.AUTH_FAILED,
            Map.of("reason", "MFA_VERIFICATION_FAILED", "mfaReason", mfaReason)
        );
    }
}

package com.wms.system.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.dto.PlatformAdminSecurityMutationResponse;
import com.wms.system.platform.model.PlatformAdminCommand;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformAccessGrantRepository;
import com.wms.system.platform.repository.PlatformAdminCommandRepository;
import com.wms.system.platform.repository.PlatformMfaChallengeRepository;
import com.wms.system.platform.repository.PlatformRoleRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.platform.repository.PlatformUserRoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformAdminSecurityMutationExecutor {
    private final PlatformAdminCommandRepository commands;
    private final PlatformMfaChallengeRepository challenges;
    private final PlatformRoleRepository roles;
    private final PlatformUserRepository users;
    private final PlatformUserRoleRepository userRoles;
    private final PlatformAccessGrantRepository accessGrants;
    private final PasswordEncoder passwordEncoder;
    private final PlatformMfaCrypto crypto;
    private final PlatformMfaProperties properties;
    private final PlatformAuditService audit;
    private final ObjectMapper objectMapper;

    @Transactional(noRollbackFor = BusinessException.class)
    public PlatformAdminSecurityMutationResponse execute(
        Long actorId,
        Long targetUserId,
        PlatformAdminSecurityOperation operation,
        String idempotencyKey,
        String reason,
        String challengeToken,
        String password,
        String code,
        HttpServletRequest http
    ) {
        String fingerprint = requestFingerprint(operation, targetUserId, reason);
        boolean ownsReservation = commands.insertReservation(
            actorId,
            targetUserId,
            idempotencyKey,
            operation.commandAction(),
            fingerprint
        ) == 1;
        PlatformAdminCommand command = commands.findByActorAndKeyForUpdate(actorId, idempotencyKey)
            .orElseThrow(() -> new IllegalStateException(
                "Platform administrator command reservation is missing"
            ));

        if (!ownsReservation) {
            validateReplay(command, targetUserId, operation.commandAction(), fingerprint);
            if ("SUCCEEDED".equals(command.getStatus())) {
                return response(command.getSafeResponseJson());
            }
            throw new BusinessException(
                ErrorKeys.PLATFORM_ADMIN_CONCURRENT_MODIFICATION,
                Map.of("targetUserId", targetUserId)
            );
        }

        try {
            PlatformMfaChallenge challenge = requireChallenge(
                operation,
                actorId,
                targetUserId,
                challengeToken
            );

            // Keep the same global lock order used by status and invitation
            // commands: governance role, target account, actor account.
            roles.findByRoleCodeForUpdate(PlatformAdminStatusService.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Required platform role is missing"));
            PlatformUser target = users.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.PLATFORM_ADMIN_NOT_FOUND,
                    Map.of("targetUserId", targetUserId)
                ));
            PlatformUser actor = users.findByIdForUpdate(actorId)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getEnabled()))
                .orElseThrow(() -> new BusinessException(ErrorKeys.AUTH_ACCESS_DENIED));
            List<String> actorRoles = userRoles.findRoleCodesByPlatformUserId(actorId);
            if (!actorRoles.contains(PlatformAdminStatusService.SUPER_ADMIN)) {
                throw new BusinessException(ErrorKeys.AUTH_ACCESS_DENIED);
            }

            validateTargetVersion(challenge, target);
            validateTarget(operation, target);
            verifyActor(actor, challenge, password, code);
            resetActorFailures(actor);

            List<String> targetRoles = userRoles.findRoleCodesByPlatformUserId(targetUserId);
            if (operation == PlatformAdminSecurityOperation.MFA_RESET) {
                clearTargetMfa(target);
            }
            target.setSecurityVersion(target.getSecurityVersion() + 1);

            OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);
            challenges.deletePendingOwnedByPlatformUserId(targetUserId);
            challenge.setConsumedAt(completedAt);
            users.save(target);
            users.save(actor);
            challenges.save(challenge);

            long activeGrantCount = accessGrants.countActiveByPlatformUserId(targetUserId, completedAt);
            PlatformAdminSecurityMutationResponse result = new PlatformAdminSecurityMutationResponse(
                targetUserId,
                operation.auditAction(),
                true,
                true,
                mfaStatus(target, completedAt),
                List.copyOf(targetRoles),
                activeGrantCount,
                target.getSecurityVersion(),
                completedAt
            );
            command.setStatus("SUCCEEDED");
            command.setSafeResponseJson(json(result));
            command.setCompletedAt(completedAt);
            commands.save(command);
            audit.recordAdminInCurrentTransaction(
                actorId,
                targetUserId,
                operation.auditAction(),
                operation.resourceType(),
                "SUCCESS",
                successDetail(result, reason, idempotencyKey),
                http
            );
            return result;
        } catch (BusinessException exception) {
            commands.delete(command);
            commands.flush();
            throw exception;
        }
    }

    private PlatformMfaChallenge requireChallenge(
        PlatformAdminSecurityOperation operation,
        Long actorId,
        Long targetUserId,
        String challengeToken
    ) {
        if (challengeToken == null || challengeToken.isBlank()) {
            throw invalidMfa("CHALLENGE_INVALID");
        }
        PlatformMfaChallenge challenge = challenges
            .findByTokenHashForUpdate(crypto.hashToken(challengeToken))
            .orElseThrow(() -> invalidMfa("CHALLENGE_INVALID"));
        OffsetDateTime now = OffsetDateTime.now();
        if (!operation.purpose().equals(challenge.getPurpose())
            || challenge.getConsumedAt() != null
            || !actorId.equals(challenge.getPlatformUserId())
            || !targetUserId.equals(challenge.getTargetPlatformUserId())
            || !PlatformAdminSecurityService.contextHash(crypto, operation, targetUserId)
                .equals(challenge.getActionContextHash())) {
            throw invalidMfa("CHALLENGE_INVALID");
        }
        if (!now.isBefore(challenge.getExpiresAt())) {
            throw invalidMfa("CHALLENGE_EXPIRED");
        }
        if (challenge.getAttemptCount() >= properties.getMaxAttempts()) {
            throw invalidMfa("TEMPORARILY_LOCKED");
        }
        return challenge;
    }

    private void validateTargetVersion(PlatformMfaChallenge challenge, PlatformUser target) {
        if (challenge.getTargetSecurityVersion() == null
            || !challenge.getTargetSecurityVersion().equals(target.getSecurityVersion())) {
            throw new BusinessException(
                ErrorKeys.PLATFORM_ADMIN_CONCURRENT_MODIFICATION,
                Map.of("targetUserId", target.getId())
            );
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

    private void verifyActor(
        PlatformUser actor,
        PlatformMfaChallenge challenge,
        String password,
        String code
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        if (actor.getMfaLockedUntil() != null && now.isBefore(actor.getMfaLockedUntil())) {
            throw invalidMfa("TEMPORARILY_LOCKED");
        }
        if (actor.getMfaLockedUntil() != null) {
            resetActorFailures(actor);
        }
        boolean passwordValid = password != null
            && passwordEncoder.matches(password, actor.getPasswordHash());
        boolean totpValid = Boolean.TRUE.equals(actor.getMfaEnabled())
            && actor.getMfaSecretEncrypted() != null
            && code != null
            && code.matches("\\d{6}")
            && crypto.verifyTotp(
                crypto.decrypt(actor.getMfaSecretEncrypted()),
                code,
                java.time.Instant.now()
            );
        if (!passwordValid || !totpValid) {
            int attempts = Math.min(properties.getMaxAttempts(), actor.getMfaFailedAttempts() + 1);
            actor.setMfaFailedAttempts(attempts);
            challenge.setAttemptCount(Math.min(
                properties.getMaxAttempts(),
                challenge.getAttemptCount() + 1
            ));
            if (attempts >= properties.getMaxAttempts()) {
                actor.setMfaLockedUntil(now.plusMinutes(properties.getLockMinutes()));
            }
            users.save(actor);
            challenges.save(challenge);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("reason", "MFA_VERIFICATION_FAILED");
            params.put(
                "mfaReason",
                attempts >= properties.getMaxAttempts() ? "TEMPORARILY_LOCKED" : "CODE_INVALID"
            );
            params.put("remainingAttempts", Math.max(0, properties.getMaxAttempts() - attempts));
            if (attempts >= properties.getMaxAttempts()) {
                params.put("retryAfterSeconds", properties.getLockMinutes() * 60);
            }
            throw new BusinessException(ErrorKeys.AUTH_FAILED, params);
        }
    }

    private void clearTargetMfa(PlatformUser target) {
        target.setMfaEnabled(false);
        target.setMfaSecretEncrypted(null);
        target.setRecoveryCodeHashesJson(null);
        target.setMfaEnrolledAt(null);
        target.setMfaFailedAttempts(0);
        target.setMfaLockedUntil(null);
    }

    private String mfaStatus(PlatformUser target, OffsetDateTime now) {
        if (!Boolean.TRUE.equals(target.getMfaEnabled()) || target.getMfaSecretEncrypted() == null) {
            return "NOT_ENROLLED";
        }
        if (target.getMfaLockedUntil() != null && now.isBefore(target.getMfaLockedUntil())) {
            return "TEMPORARILY_LOCKED";
        }
        return "ENROLLED";
    }

    private void validateReplay(
        PlatformAdminCommand command,
        Long targetUserId,
        String action,
        String fingerprint
    ) {
        if (!targetUserId.equals(command.getTargetPlatformUserId())
            || !action.equals(command.getAction())
            || !fingerprint.equals(command.getRequestFingerprint())) {
            throw new BusinessException(ErrorKeys.PLATFORM_ADMIN_IDEMPOTENCY_CONFLICT);
        }
    }

    private String requestFingerprint(
        PlatformAdminSecurityOperation operation,
        Long targetUserId,
        String reason
    ) {
        return crypto.hashToken(operation.commandAction() + "\n" + targetUserId + "\n" + reason);
    }

    private Map<String, Object> successDetail(
        PlatformAdminSecurityMutationResponse response,
        String reason,
        String idempotencyKey
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetPlatformUserId", response.targetUserId());
        detail.put("changed", response.changed());
        detail.put("enabled", response.enabled());
        detail.put("mfaStatus", response.mfaStatus());
        detail.put("roles", response.roles());
        detail.put("activeGrantCount", response.activeGrantCount());
        detail.put("securityVersion", response.securityVersion());
        detail.put("sessionsRevoked", true);
        detail.put("passwordPreserved", true);
        detail.put("rolesPreserved", true);
        detail.put("accessGrantsPreserved", true);
        detail.put("backgroundTasksPreserved", true);
        detail.put("reason", reason);
        detail.put("idempotencyKeyHash", crypto.hashToken(idempotencyKey));
        return detail;
    }

    private void resetActorFailures(PlatformUser actor) {
        actor.setMfaFailedAttempts(0);
        actor.setMfaLockedUntil(null);
    }

    private String json(PlatformAdminSecurityMutationResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                "Unable to persist safe platform command response",
                exception
            );
        }
    }

    private PlatformAdminSecurityMutationResponse response(String json) {
        try {
            return objectMapper.readValue(json, PlatformAdminSecurityMutationResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                "Unable to read safe platform command response",
                exception
            );
        }
    }

    private BusinessException invalidMfa(String mfaReason) {
        return new BusinessException(
            ErrorKeys.AUTH_FAILED,
            Map.of("reason", "MFA_VERIFICATION_FAILED", "mfaReason", mfaReason)
        );
    }
}

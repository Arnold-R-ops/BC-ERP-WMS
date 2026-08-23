package com.wms.system.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.dto.PlatformAdminMutationResponse;
import com.wms.system.platform.dto.PlatformAdminStatusMutationRequest;
import com.wms.system.platform.model.PlatformAdminCommand;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformAccessGrantRepository;
import com.wms.system.platform.repository.PlatformAdminCommandRepository;
import com.wms.system.platform.repository.PlatformAdminInvitationRepository;
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
public class PlatformAdminStatusMutationExecutor {
    private final PlatformAdminCommandRepository commands;
    private final PlatformMfaChallengeRepository challenges;
    private final PlatformRoleRepository roles;
    private final PlatformUserRepository users;
    private final PlatformUserRoleRepository userRoles;
    private final PlatformAdminInvitationRepository invitations;
    private final PlatformAccessGrantRepository accessGrants;
    private final PasswordEncoder passwordEncoder;
    private final PlatformMfaCrypto crypto;
    private final PlatformMfaProperties properties;
    private final PlatformAuditService audit;
    private final ObjectMapper objectMapper;

    @Transactional(noRollbackFor = BusinessException.class)
    public PlatformAdminMutationResponse execute(
        Long actorId,
        Long targetUserId,
        boolean desiredEnabled,
        String idempotencyKey,
        String reason,
        PlatformAdminStatusMutationRequest request,
        HttpServletRequest http
    ) {
        String commandAction = desiredEnabled ? "ADMIN_ENABLE" : "ADMIN_DISABLE";
        String auditAction = PlatformAdminStatusService.auditAction(desiredEnabled);
        String fingerprint = requestFingerprint(targetUserId, desiredEnabled, reason);
        boolean ownsReservation = commands.insertReservation(
            actorId, targetUserId, idempotencyKey, commandAction, fingerprint) == 1;
        PlatformAdminCommand command = commands.findByActorAndKeyForUpdate(actorId, idempotencyKey)
            .orElseThrow(() -> new IllegalStateException("Platform administrator command reservation is missing"));

        if (!ownsReservation) {
            validateReplay(command, targetUserId, commandAction, fingerprint);
            if ("SUCCEEDED".equals(command.getStatus())) return response(command.getSafeResponseJson());
            throw new BusinessException(ErrorKeys.PLATFORM_ADMIN_CONCURRENT_MODIFICATION,
                Map.of("targetUserId", targetUserId));
        }

        try {
            PlatformMfaChallenge challenge = requireChallenge(request, actorId, targetUserId, desiredEnabled);

            // Every command that may affect a super-administrator seat takes
            // this mutex first. Invitation create/accept uses the same row.
            roles.findByRoleCodeForUpdate(PlatformAdminStatusService.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Required platform role is missing"));
            PlatformUser target = users.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorKeys.PLATFORM_ADMIN_NOT_FOUND,
                    Map.of("targetUserId", targetUserId)));
            PlatformUser actor = users.findByIdForUpdate(actorId)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getEnabled()))
                .orElseThrow(() -> new BusinessException(ErrorKeys.AUTH_ACCESS_DENIED));
            List<String> actorRoles = userRoles.findRoleCodesByPlatformUserId(actorId);
            if (!actorRoles.contains(PlatformAdminStatusService.SUPER_ADMIN)) {
                throw new BusinessException(ErrorKeys.AUTH_ACCESS_DENIED);
            }

            validateTargetVersion(challenge, target);
            verifyActor(actor, challenge, request);
            resetActorFailures(actor);
            users.save(actor);

            List<String> targetRoles = userRoles.findRoleCodesByPlatformUserId(targetUserId);
            boolean beforeEnabled = Boolean.TRUE.equals(target.getEnabled());
            boolean changed = beforeEnabled != desiredEnabled;
            if (changed) validateFinalTransition(target, targetRoles, desiredEnabled);

            OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);
            if (changed) {
                target.setEnabled(desiredEnabled);
                target.setSecurityVersion(target.getSecurityVersion() + 1);
                challenges.deletePendingOwnedByPlatformUserId(targetUserId);
                users.save(target);
            }
            challenge.setConsumedAt(completedAt);
            challenges.save(challenge);

            long activeGrantCount = accessGrants.countActiveByPlatformUserId(targetUserId, completedAt);
            PlatformAdminMutationResponse result = new PlatformAdminMutationResponse(
                targetUserId,
                auditAction,
                changed,
                desiredEnabled,
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
                auditAction,
                "platform_admin_status",
                "SUCCESS",
                successDetail(result, beforeEnabled, reason, idempotencyKey),
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
        PlatformAdminStatusMutationRequest request,
        Long actorId,
        Long targetUserId,
        boolean desiredEnabled
    ) {
        if (request == null || request.getChallengeToken() == null || request.getChallengeToken().isBlank()) {
            throw invalidMfa("CHALLENGE_INVALID");
        }
        PlatformMfaChallenge challenge = challenges
            .findByTokenHashForUpdate(crypto.hashToken(request.getChallengeToken()))
            .orElseThrow(() -> invalidMfa("CHALLENGE_INVALID"));
        OffsetDateTime now = OffsetDateTime.now();
        if (!PlatformAdminStatusService.STATUS_CHANGE_PURPOSE.equals(challenge.getPurpose())
            || challenge.getConsumedAt() != null
            || !actorId.equals(challenge.getPlatformUserId())
            || !targetUserId.equals(challenge.getTargetPlatformUserId())
            || !PlatformAdminStatusService.contextHash(crypto, targetUserId, desiredEnabled)
                .equals(challenge.getActionContextHash())) {
            throw invalidMfa("CHALLENGE_INVALID");
        }
        if (!now.isBefore(challenge.getExpiresAt())) throw invalidMfa("CHALLENGE_EXPIRED");
        if (challenge.getAttemptCount() >= properties.getMaxAttempts()) {
            throw invalidMfa("TEMPORARILY_LOCKED");
        }
        return challenge;
    }

    private void validateTargetVersion(PlatformMfaChallenge challenge, PlatformUser target) {
        if (challenge.getTargetSecurityVersion() == null
            || !challenge.getTargetSecurityVersion().equals(target.getSecurityVersion())) {
            throw new BusinessException(ErrorKeys.PLATFORM_ADMIN_STATUS_CONFLICT,
                Map.of("targetUserId", target.getId()));
        }
    }

    private void verifyActor(
        PlatformUser actor,
        PlatformMfaChallenge challenge,
        PlatformAdminStatusMutationRequest request
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        if (actor.getMfaLockedUntil() != null && now.isBefore(actor.getMfaLockedUntil())) {
            throw invalidMfa("TEMPORARILY_LOCKED");
        }
        if (actor.getMfaLockedUntil() != null) resetActorFailures(actor);
        boolean passwordValid = request.getPassword() != null
            && passwordEncoder.matches(request.getPassword(), actor.getPasswordHash());
        boolean totpValid = Boolean.TRUE.equals(actor.getMfaEnabled())
            && actor.getMfaSecretEncrypted() != null
            && request.getCode() != null
            && request.getCode().matches("\\d{6}")
            && crypto.verifyTotp(
                crypto.decrypt(actor.getMfaSecretEncrypted()),
                request.getCode(),
                java.time.Instant.now()
            );
        if (!passwordValid || !totpValid) {
            int attempts = Math.min(properties.getMaxAttempts(), actor.getMfaFailedAttempts() + 1);
            actor.setMfaFailedAttempts(attempts);
            challenge.setAttemptCount(Math.min(properties.getMaxAttempts(), challenge.getAttemptCount() + 1));
            if (attempts >= properties.getMaxAttempts()) {
                actor.setMfaLockedUntil(now.plusMinutes(properties.getLockMinutes()));
            }
            users.save(actor);
            challenges.save(challenge);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("reason", "MFA_VERIFICATION_FAILED");
            params.put("mfaReason", attempts >= properties.getMaxAttempts()
                ? "TEMPORARILY_LOCKED" : "CODE_INVALID");
            params.put("remainingAttempts", Math.max(0, properties.getMaxAttempts() - attempts));
            if (attempts >= properties.getMaxAttempts()) {
                params.put("retryAfterSeconds", properties.getLockMinutes() * 60);
            }
            throw new BusinessException(ErrorKeys.AUTH_FAILED, params);
        }
    }

    private void validateFinalTransition(PlatformUser target, List<String> targetRoles, boolean desiredEnabled) {
        boolean superAdmin = targetRoles.contains(PlatformAdminStatusService.SUPER_ADMIN);
        if (!desiredEnabled && superAdmin
            && userRoles.countEnabledUsersByRoleCode(PlatformAdminStatusService.SUPER_ADMIN) <= 1) {
            throw new BusinessException(ErrorKeys.PLATFORM_ADMIN_LAST_SUPER_ADMIN,
                Map.of("targetUserId", target.getId()));
        }
        if (desiredEnabled) {
            PlatformAdminStatusService.requireSafeEnableRoles(target.getId(), targetRoles);
            if (superAdmin) {
                long enabled = userRoles.countEnabledUsersByRoleCode(PlatformAdminStatusService.SUPER_ADMIN);
                long pending = invitations.countActiveByRoleCode(
                    PlatformAdminStatusService.SUPER_ADMIN, OffsetDateTime.now());
                if (enabled + pending >= 2) {
                    throw new BusinessException(ErrorKeys.PLATFORM_ADMIN_SUPER_ADMIN_LIMIT,
                        Map.of("maximumActiveSeats", 2));
                }
            }
        }
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

    private String requestFingerprint(Long targetUserId, boolean desiredEnabled, String reason) {
        return crypto.hashToken("ADMIN_STATUS_COMMAND\n" + targetUserId + "\n" + desiredEnabled + "\n" + reason);
    }

    private Map<String, Object> successDetail(
        PlatformAdminMutationResponse response,
        boolean beforeEnabled,
        String reason,
        String idempotencyKey
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetPlatformUserId", response.targetUserId());
        detail.put("beforeEnabled", beforeEnabled);
        detail.put("afterEnabled", response.enabled());
        detail.put("changed", response.changed());
        detail.put("roles", response.roles());
        detail.put("activeGrantCount", response.activeGrantCount());
        detail.put("securityVersion", response.securityVersion());
        detail.put("reason", reason);
        detail.put("idempotencyKeyHash", crypto.hashToken(idempotencyKey));
        return detail;
    }

    private void resetActorFailures(PlatformUser actor) {
        actor.setMfaFailedAttempts(0);
        actor.setMfaLockedUntil(null);
    }

    private String json(PlatformAdminMutationResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to persist safe platform command response", exception);
        }
    }

    private PlatformAdminMutationResponse response(String json) {
        try {
            return objectMapper.readValue(json, PlatformAdminMutationResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read safe platform command response", exception);
        }
    }

    private BusinessException invalidMfa(String mfaReason) {
        return new BusinessException(ErrorKeys.AUTH_FAILED,
            Map.of("reason", "MFA_VERIFICATION_FAILED", "mfaReason", mfaReason));
    }
}

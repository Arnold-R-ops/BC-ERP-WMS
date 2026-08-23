package com.wms.system.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.dto.PlatformAdminRoleChangeMutationRequest;
import com.wms.system.platform.dto.PlatformAdminRoleChangeResponse;
import com.wms.system.platform.model.PlatformAdminCommand;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformRole;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.model.PlatformUserRole;
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
public class PlatformAdminRoleMutationExecutor {
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
    public PlatformAdminRoleChangeResponse execute(
        Long actorId,
        Long targetUserId,
        String desiredRole,
        String idempotencyKey,
        String reason,
        PlatformAdminRoleChangeMutationRequest request,
        HttpServletRequest http
    ) {
        String fingerprint = requestFingerprint(targetUserId, desiredRole, reason);
        boolean ownsReservation = commands.insertReservation(
            actorId,
            targetUserId,
            idempotencyKey,
            PlatformAdminRoleService.ROLE_CHANGE_COMMAND,
            fingerprint
        ) == 1;
        PlatformAdminCommand command = commands.findByActorAndKeyForUpdate(actorId, idempotencyKey)
            .orElseThrow(() -> new IllegalStateException(
                "Platform administrator command reservation is missing"));
        if (!ownsReservation) {
            validateReplay(command, targetUserId, fingerprint);
            if ("SUCCEEDED".equals(command.getStatus())) return response(command.getSafeResponseJson());
            throw new BusinessException(
                ErrorKeys.PLATFORM_ADMIN_CONCURRENT_MODIFICATION,
                Map.of("targetUserId", targetUserId)
            );
        }

        try {
            PlatformMfaChallenge challenge = requireChallenge(
                request, actorId, targetUserId, desiredRole);

            roles.findByRoleCodeForUpdate(PlatformAdminStatusService.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Required platform role is missing"));
            PlatformRole desired = roles.findByRoleCodeForUpdate(desiredRole)
                .orElseThrow(() -> new IllegalStateException("Required platform role is missing"));
            PlatformUser target = users.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.PLATFORM_ADMIN_NOT_FOUND, Map.of("targetUserId", targetUserId)));
            PlatformUser actor = users.findByIdForUpdate(actorId)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getEnabled()))
                .orElseThrow(() -> new BusinessException(ErrorKeys.AUTH_ACCESS_DENIED));
            if (!userRoles.findRoleCodesByPlatformUserId(actorId)
                .contains(PlatformAdminStatusService.SUPER_ADMIN)) {
                throw new BusinessException(ErrorKeys.AUTH_ACCESS_DENIED);
            }

            validateTargetVersion(challenge, target);
            verifyActor(actor, challenge, request);
            resetActorFailures(actor);
            users.save(actor);

            List<String> beforeRoles = userRoles.findRoleCodesByPlatformUserId(targetUserId);
            OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);
            validateTargetRole(targetUserId, beforeRoles, desiredRole, completedAt);
            boolean changed = beforeRoles.size() != 1 || !beforeRoles.contains(desiredRole);
            if (changed) {
                List<PlatformUserRole> assignments = userRoles.findByPlatformUserId(targetUserId);
                userRoles.deleteAllInBatch(assignments);
                userRoles.saveAndFlush(PlatformUserRole.builder()
                    .platformUserId(targetUserId)
                    .platformRoleId(desired.getId())
                    .build());
                target.setSecurityVersion(target.getSecurityVersion() + 1);
                challenges.deletePendingOwnedByPlatformUserId(targetUserId);
                users.save(target);
            }
            challenge.setConsumedAt(completedAt);
            challenges.save(challenge);

            long activeGrantCount = accessGrants.countActiveByPlatformUserId(targetUserId, completedAt);
            PlatformAdminRoleChangeResponse result = new PlatformAdminRoleChangeResponse(
                targetUserId,
                changed,
                Boolean.TRUE.equals(target.getEnabled()),
                List.copyOf(beforeRoles),
                List.of(desiredRole),
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
                PlatformAdminRoleService.ROLE_CHANGE_AUDIT,
                "platform_admin_roles",
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
        PlatformAdminRoleChangeMutationRequest request,
        Long actorId,
        Long targetUserId,
        String desiredRole
    ) {
        if (request == null || request.getChallengeToken() == null
            || request.getChallengeToken().isBlank()) {
            throw invalidMfa("CHALLENGE_INVALID");
        }
        PlatformMfaChallenge challenge = challenges
            .findByTokenHashForUpdate(crypto.hashToken(request.getChallengeToken()))
            .orElseThrow(() -> invalidMfa("CHALLENGE_INVALID"));
        OffsetDateTime now = OffsetDateTime.now();
        if (!PlatformAdminRoleService.ROLE_CHANGE_PURPOSE.equals(challenge.getPurpose())
            || challenge.getConsumedAt() != null
            || !actorId.equals(challenge.getPlatformUserId())
            || !targetUserId.equals(challenge.getTargetPlatformUserId())
            || !PlatformAdminRoleService.contextHash(crypto, targetUserId, desiredRole)
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
            throw new BusinessException(
                ErrorKeys.PLATFORM_ADMIN_CONCURRENT_MODIFICATION,
                Map.of("targetUserId", target.getId())
            );
        }
    }

    private void verifyActor(
        PlatformUser actor,
        PlatformMfaChallenge challenge,
        PlatformAdminRoleChangeMutationRequest request
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
            challenge.setAttemptCount(Math.min(
                properties.getMaxAttempts(), challenge.getAttemptCount() + 1));
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

    private void validateReplay(
        PlatformAdminCommand command,
        Long targetUserId,
        String fingerprint
    ) {
        if (!targetUserId.equals(command.getTargetPlatformUserId())
            || !PlatformAdminRoleService.ROLE_CHANGE_COMMAND.equals(command.getAction())
            || !fingerprint.equals(command.getRequestFingerprint())) {
            throw new BusinessException(ErrorKeys.PLATFORM_ADMIN_IDEMPOTENCY_CONFLICT);
        }
    }

    private void validateTargetRole(
        Long targetUserId,
        List<String> currentRoles,
        String desiredRole,
        OffsetDateTime now
    ) {
        PlatformAdminRoleService.validateOrdinaryRoleSet(targetUserId, currentRoles);
        if (PlatformAdminRoleService.SECURITY_AUDITOR.equals(desiredRole)) {
            long blockingGrants = accessGrants.countCurrentOrFutureByPlatformUserId(targetUserId, now);
            if (blockingGrants > 0) {
                throw new BusinessException(
                    ErrorKeys.PLATFORM_ADMIN_ACTIVE_GRANTS_EXIST,
                    Map.of("targetUserId", targetUserId, "blockingGrantCount", blockingGrants)
                );
            }
        }
    }

    private String requestFingerprint(Long targetUserId, String desiredRole, String reason) {
        return crypto.hashToken(
            PlatformAdminRoleService.ROLE_CHANGE_COMMAND + "\n"
                + targetUserId + "\n" + desiredRole + "\n" + reason
        );
    }

    private Map<String, Object> successDetail(
        PlatformAdminRoleChangeResponse response,
        String reason,
        String idempotencyKey
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetPlatformUserId", response.targetUserId());
        detail.put("changed", response.changed());
        detail.put("enabledPreserved", response.enabled());
        detail.put("beforeRoles", response.beforeRoles());
        detail.put("afterRoles", response.afterRoles());
        detail.put("activeGrantCount", response.activeGrantCount());
        detail.put("securityVersion", response.securityVersion());
        detail.put("sessionsRevoked", response.changed());
        detail.put("accessGrantsPreserved", true);
        detail.put("reason", reason);
        detail.put("idempotencyKeyHash", crypto.hashToken(idempotencyKey));
        return detail;
    }

    private void resetActorFailures(PlatformUser actor) {
        actor.setMfaFailedAttempts(0);
        actor.setMfaLockedUntil(null);
    }

    private String json(PlatformAdminRoleChangeResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to persist safe platform command response", exception);
        }
    }

    private PlatformAdminRoleChangeResponse response(String json) {
        try {
            return objectMapper.readValue(json, PlatformAdminRoleChangeResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read safe platform command response", exception);
        }
    }

    private BusinessException invalidMfa(String reason) {
        return new BusinessException(
            ErrorKeys.AUTH_FAILED,
            Map.of("reason", "MFA_VERIFICATION_FAILED", "mfaReason", reason)
        );
    }
}

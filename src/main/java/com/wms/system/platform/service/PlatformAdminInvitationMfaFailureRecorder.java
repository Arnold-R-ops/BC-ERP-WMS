package com.wms.system.platform.service;

import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformMfaChallengeRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformAdminInvitationMfaFailureRecorder {
    private static final String PURPOSE = "ADMIN_INVITE";

    private final PlatformMfaChallengeRepository challenges;
    private final PlatformUserRepository users;
    private final PlatformMfaProperties properties;
    private final PlatformAuditService audit;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int record(
        Long actorId,
        String challengeTokenHash,
        HttpServletRequest http
    ) {
        PlatformMfaChallenge challenge = challenges
            .findByTokenHashForUpdate(challengeTokenHash)
            .orElse(null);
        OffsetDateTime now = OffsetDateTime.now();
        if (challenge == null
            || !PURPOSE.equals(challenge.getPurpose())
            || !actorId.equals(challenge.getPlatformUserId())
            || challenge.getConsumedAt() != null
            || !now.isBefore(challenge.getExpiresAt())) {
            return 0;
        }

        PlatformUser actor = users.findByIdForUpdate(actorId).orElse(null);
        if (actor == null) return 0;
        if (challenge.getAttemptCount() >= properties.getMaxAttempts()) {
            return properties.getMaxAttempts();
        }
        if (actor.getMfaLockedUntil() != null && now.isBefore(actor.getMfaLockedUntil())) {
            return properties.getMaxAttempts();
        }
        if (actor.getMfaLockedUntil() != null) {
            actor.setMfaFailedAttempts(0);
            actor.setMfaLockedUntil(null);
        }

        int attempts = Math.min(
            properties.getMaxAttempts(), actor.getMfaFailedAttempts() + 1);
        actor.setMfaFailedAttempts(attempts);
        challenge.setAttemptCount(Math.min(
            properties.getMaxAttempts(), challenge.getAttemptCount() + 1));
        if (attempts >= properties.getMaxAttempts()) {
            actor.setMfaLockedUntil(now.plusMinutes(properties.getLockMinutes()));
        }
        users.save(actor);
        challenges.save(challenge);
        audit.recordInCurrentTransaction(
            actorId,
            null,
            "MFA_FAILED",
            "platform_identity",
            "FAILED",
            Map.of(
                "attempts", attempts,
                "locked", attempts >= properties.getMaxAttempts(),
                "operation", PURPOSE
            ),
            http
        );
        return attempts;
    }
}

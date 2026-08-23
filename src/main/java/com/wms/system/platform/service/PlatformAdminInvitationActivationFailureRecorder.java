package com.wms.system.platform.service;

import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.platform.model.PlatformAdminInvitationActivation;
import com.wms.system.platform.repository.PlatformAdminInvitationActivationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class PlatformAdminInvitationActivationFailureRecorder {
    private final PlatformAdminInvitationActivationRepository activations;
    private final PlatformMfaProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int record(String tokenHash) {
        PlatformAdminInvitationActivation activation = activations
            .findByTokenHashForUpdate(tokenHash)
            .orElse(null);
        if (activation == null || activation.getConsumedAt() != null
            || !OffsetDateTime.now().isBefore(activation.getExpiresAt())) {
            return 0;
        }
        int attempts = Math.min(
            properties.getMaxAttempts(), activation.getAttemptCount() + 1);
        activation.setAttemptCount(attempts);
        activations.saveAndFlush(activation);
        return attempts;
    }
}

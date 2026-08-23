package com.wms.system.platform.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;

import java.util.Map;

/**
 * Internal signal for a valid administrator-invitation challenge whose
 * password or authenticator code was rejected.
 */
final class PlatformAdminInvitationMfaCredentialException extends BusinessException {
    private final Long actorId;
    private final String challengeTokenHash;

    PlatformAdminInvitationMfaCredentialException(Long actorId, String challengeTokenHash) {
        super(ErrorKeys.AUTH_FAILED, Map.of("reason", "MFA_VERIFICATION_FAILED"));
        this.actorId = actorId;
        this.challengeTokenHash = challengeTokenHash;
    }

    Long actorId() {
        return actorId;
    }

    String challengeTokenHash() {
        return challengeTokenHash;
    }
}

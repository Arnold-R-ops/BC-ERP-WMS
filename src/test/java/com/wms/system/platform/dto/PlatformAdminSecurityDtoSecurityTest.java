package com.wms.system.platform.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAdminSecurityDtoSecurityTest {
    @Test
    void requestsAndChallengeResponseRedactSecretsAndOperationalReason() {
        PlatformAdminSessionRevokeRequest sessionRequest = new PlatformAdminSessionRevokeRequest();
        sessionRequest.setChallengeToken("raw-session-challenge");
        sessionRequest.setPassword("current-password");
        sessionRequest.setCode("123456");
        sessionRequest.setReason("private incident reason");
        assertThat(sessionRequest.toString())
            .doesNotContain(
                "raw-session-challenge", "current-password", "123456", "private incident reason")
            .contains(
                "challengeToken=<redacted>", "password=<redacted>",
                "code=<redacted>", "reason=<redacted>");

        PlatformAdminMfaResetRequest mfaRequest = new PlatformAdminMfaResetRequest();
        mfaRequest.setChallengeToken("raw-mfa-challenge");
        mfaRequest.setPassword("current-password");
        mfaRequest.setCode("654321");
        mfaRequest.setReason("lost phone");
        assertThat(mfaRequest.toString())
            .doesNotContain("raw-mfa-challenge", "current-password", "654321", "lost phone");

        PlatformAdminSecurityChallengeResponse response =
            new PlatformAdminSecurityChallengeResponse("raw-challenge", 300_000L, 7L);
        assertThat(response.toString())
            .doesNotContain("raw-challenge")
            .contains("challengeToken=<redacted>", "targetSecurityVersion=7");
    }
}

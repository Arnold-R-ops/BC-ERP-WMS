package com.wms.system.platform.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAdminStatusDtoSecurityTest {
    @Test
    void requestAndChallengeToStringRedactAllCredentialsAndTokens() {
        PlatformAdminStatusMutationRequest request = new PlatformAdminStatusMutationRequest();
        request.setChallengeToken("raw-challenge-token");
        request.setPassword("current-password");
        request.setCode("123456");
        request.setReason("private operational reason");

        assertThat(request.toString())
            .doesNotContain("raw-challenge-token", "current-password", "123456", "private operational reason")
            .contains("challengeToken=<redacted>", "password=<redacted>", "code=<redacted>", "reason=<redacted>");

        PlatformAdminStatusChallengeResponse response =
            new PlatformAdminStatusChallengeResponse("raw-challenge-token", 300_000L, 7L);
        assertThat(response.toString())
            .doesNotContain("raw-challenge-token")
            .contains("challengeToken=<redacted>", "targetSecurityVersion=7");
    }
}

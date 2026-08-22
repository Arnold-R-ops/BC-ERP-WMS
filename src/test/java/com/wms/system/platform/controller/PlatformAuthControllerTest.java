package com.wms.system.platform.controller;

import com.wms.system.platform.dto.PlatformAuthResponse;
import com.wms.system.platform.dto.PlatformLoginRequest;
import com.wms.system.platform.service.PlatformMfaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAuthControllerTest {
    @Mock PlatformMfaService service;

    @Test void passwordStepReturnsOnlyRedactedMfaChallengeAndNoJwt() {
        PlatformAuthController controller = new PlatformAuthController(service);
        PlatformLoginRequest request = new PlatformLoginRequest(" Developer@BCWMS.com ", "secret-password");
        PlatformAuthResponse response = new PlatformAuthResponse("MFA_ENROLLMENT_REQUIRED",
            "challenge-secret", "totp-secret", "otpauth://secret", null, null,
            "developer@bcwms.com", List.of("PLATFORM_SUPER_ADMIN"), 0, null);
        when(service.login(request)).thenReturn(response);

        PlatformAuthResponse actual = controller.login(request);

        assertThat(actual.status()).isEqualTo("MFA_ENROLLMENT_REQUIRED");
        assertThat(actual.token()).isNull();
        assertThat(request.toString()).contains("password=[REDACTED]")
            .doesNotContain("secret-password");
        assertThat(actual.toString()).contains("challengeToken=[REDACTED]", "token=[REDACTED]")
            .doesNotContain("challenge-secret", "totp-secret", "otpauth://secret");
    }
}

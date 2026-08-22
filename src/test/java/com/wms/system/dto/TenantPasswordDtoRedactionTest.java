package com.wms.system.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenantPasswordDtoRedactionTest {

    @Test
    void tenantCredentialDtosDoNotExposeSecretsInToString() {
        String secret = "NeverLogThis-2026";

        List<Object> credentialDtos = List.of(
            LoginRequest.builder().username("tenant.user").password(secret).build(),
            ChangeMyPasswordRequest.builder().oldPassword(secret).newPassword(secret).build(),
            CreateUserRequest.builder()
                .username("tenant.user")
                .password(secret)
                .roleIds(List.of(1L))
                .build(),
            ResetPasswordResponse.builder()
                .userId(1L)
                .username("tenant.user")
                .temporaryPassword(secret)
                .mustChangePassword(true)
                .build(),
            new RevokeOwnSessionsRequest(secret)
        );

        assertThat(credentialDtos)
            .allSatisfy(dto -> assertThat(dto.toString()).doesNotContain(secret));
    }
}

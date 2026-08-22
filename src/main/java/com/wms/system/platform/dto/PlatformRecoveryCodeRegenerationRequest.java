package com.wms.system.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlatformRecoveryCodeRegenerationRequest {
    @NotBlank private String challengeToken;
    @NotBlank private String password;
    @NotBlank @Pattern(regexp = "\\d{6}") private String code;

    @Override
    public String toString() {
        return "PlatformRecoveryCodeRegenerationRequest[challengeToken=<redacted>, password=<redacted>, code=<redacted>]";
    }
}

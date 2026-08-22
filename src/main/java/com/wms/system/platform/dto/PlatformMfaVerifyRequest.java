package com.wms.system.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlatformMfaVerifyRequest {
    @NotBlank private String challengeToken;
    private String code;
    private String recoveryCode;

    @Override public String toString() {
        return "PlatformMfaVerifyRequest[challengeToken=[REDACTED], code=[REDACTED], recoveryCode=[REDACTED]]";
    }
}

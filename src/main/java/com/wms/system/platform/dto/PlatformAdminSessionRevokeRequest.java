package com.wms.system.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlatformAdminSessionRevokeRequest {
    @NotBlank
    @Size(max = 500)
    private String challengeToken;

    @NotBlank
    @Size(max = 200)
    private String password;

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    private String code;

    @NotBlank
    @Size(max = 500)
    private String reason;

    @Override
    public String toString() {
        return "PlatformAdminSessionRevokeRequest[challengeToken=<redacted>, password=<redacted>, "
            + "code=<redacted>, reason=<redacted>]";
    }
}

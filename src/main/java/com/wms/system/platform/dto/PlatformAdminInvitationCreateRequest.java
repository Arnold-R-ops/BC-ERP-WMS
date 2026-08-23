package com.wms.system.platform.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PlatformAdminInvitationCreateRequest {
    @NotBlank private String challengeToken;
    @NotBlank private String password;
    @NotBlank @Pattern(regexp="\\d{6}") private String code;
    @NotBlank @Email @Size(max=254) private String email;
    @NotBlank @Size(max=100) private String displayName;
    @NotBlank @Pattern(regexp="SUPER_ADMIN|ORDINARY_ADMIN") private String invitationType;
    @NotBlank @Pattern(regexp="PLATFORM_SUPER_ADMIN|PLATFORM_OPERATIONS_ADMIN|PLATFORM_SECURITY_AUDITOR")
    private String roleCode;
    @NotBlank @Size(max=500) private String reason;
    @Override public String toString(){return "PlatformAdminInvitationCreateRequest[credentials=<redacted>, invitation=<redacted>]";}
}

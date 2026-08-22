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
    @NotBlank @Size(max=500) private String reason;
    @Override public String toString(){return "PlatformAdminInvitationCreateRequest[credentials=<redacted>, invitation=<redacted>]";}
}

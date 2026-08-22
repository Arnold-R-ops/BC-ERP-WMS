package com.wms.system.platform.dto;
import jakarta.validation.constraints.*;
import lombok.Getter; import lombok.Setter;
@Getter @Setter public class PlatformInvitationActivateRequest {
    @NotBlank private String token;
    @NotBlank @Size(min=12,max=64) private String password;
    @Override public String toString(){return "PlatformInvitationActivateRequest[token=<redacted>,password=<redacted>]";}
}

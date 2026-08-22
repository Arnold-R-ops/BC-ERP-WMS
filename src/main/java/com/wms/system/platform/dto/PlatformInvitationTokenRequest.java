package com.wms.system.platform.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter; import lombok.Setter;
@Getter @Setter public class PlatformInvitationTokenRequest {
    @NotBlank private String token;
    @Override public String toString(){return "PlatformInvitationTokenRequest[token=<redacted>]";}
}

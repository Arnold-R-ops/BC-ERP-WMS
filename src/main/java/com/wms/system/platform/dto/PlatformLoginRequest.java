package com.wms.system.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Password-bearing requests must never render the password in diagnostic logs. */
@Getter
@Setter
public class PlatformLoginRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    private String password;

    public PlatformLoginRequest() {
    }

    public PlatformLoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    @Override
    public String toString() {
        return "PlatformLoginRequest[email=" + email + ", password=[REDACTED]]";
    }
}

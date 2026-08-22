package com.wms.system.signup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(
    @NotBlank String signupId,
    @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code
) {}

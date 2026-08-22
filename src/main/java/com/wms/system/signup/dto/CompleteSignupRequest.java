package com.wms.system.signup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompleteSignupRequest(
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @NotBlank @Size(max = 160) String companyName,
    @NotBlank @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9-]{1,28}[a-zA-Z0-9]$") String slug,
    @NotBlank @Size(min = 8, max = 128)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).+$") String password
) {}

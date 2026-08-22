package com.wms.system.signup.dto;

import com.wms.system.subscription.model.PlanCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BeginSignupRequest(
    @NotBlank @Email String email,
    @NotNull PlanCode planCode
) {}

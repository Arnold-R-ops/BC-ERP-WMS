package com.wms.system.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PlatformAdminRoleChangeChallengeRequest(
    @NotBlank
    @Pattern(regexp = "PLATFORM_(OPERATIONS_ADMIN|SECURITY_AUDITOR)")
    String jobRoleCode
) { }

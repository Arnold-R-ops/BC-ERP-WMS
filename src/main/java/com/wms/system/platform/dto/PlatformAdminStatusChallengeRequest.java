package com.wms.system.platform.dto;

import jakarta.validation.constraints.NotNull;

public record PlatformAdminStatusChallengeRequest(
    @NotNull Boolean desiredEnabled
) { }

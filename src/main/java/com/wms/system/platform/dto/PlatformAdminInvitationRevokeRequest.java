package com.wms.system.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlatformAdminInvitationRevokeRequest(
    @NotBlank @Size(max = 500) String reason
) { }

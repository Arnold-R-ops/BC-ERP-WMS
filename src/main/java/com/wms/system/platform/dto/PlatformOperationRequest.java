package com.wms.system.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record PlatformOperationRequest(
    @NotBlank String operationType,
    @NotBlank String resourceType,
    @NotBlank String resourceId,
    @NotNull Map<String, Object> payload,
    @NotBlank String reason
) {}

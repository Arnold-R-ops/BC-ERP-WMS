package com.wms.system.platform.dto;

public record PlatformEffectiveAccessScopeResponse(
    Long tenantId,
    String datasetCode,
    boolean read,
    boolean export
) { }

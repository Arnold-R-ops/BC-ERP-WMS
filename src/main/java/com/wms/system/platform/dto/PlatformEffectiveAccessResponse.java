package com.wms.system.platform.dto;

import java.util.List;

public record PlatformEffectiveAccessResponse(
    boolean superAdmin,
    String tenantDirectoryScope,
    List<PlatformEffectiveAccessScopeResponse> scopes
) { }

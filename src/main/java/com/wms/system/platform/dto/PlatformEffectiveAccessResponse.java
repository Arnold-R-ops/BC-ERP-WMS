package com.wms.system.platform.dto;

import java.util.List;

public record PlatformEffectiveAccessResponse(
    boolean superAdmin,
    List<PlatformEffectiveAccessScopeResponse> scopes
) { }

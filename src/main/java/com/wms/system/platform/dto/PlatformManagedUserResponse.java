package com.wms.system.platform.dto;
public record PlatformManagedUserResponse(
    Long id,
    String email,
    String displayName,
    boolean enabled,
    boolean superAdmin,
    boolean mfaEnabled
) { }

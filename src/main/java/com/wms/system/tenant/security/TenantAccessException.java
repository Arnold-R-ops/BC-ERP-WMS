package com.wms.system.tenant.security;

public final class TenantAccessException extends RuntimeException {
    private final String errorKey;
    private final int status;

    public TenantAccessException(String errorKey, int status) {
        super(errorKey);
        this.errorKey = errorKey;
        this.status = status;
    }

    public String errorKey() { return errorKey; }
    public int status() { return status; }
}

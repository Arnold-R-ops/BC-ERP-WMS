package com.wms.system.platform.service;

/** Never exposes a token, session identifier or database detail. */
public class PlatformSessionException extends RuntimeException {
    private final String errorKey;
    public PlatformSessionException(String errorKey) {
        super(errorKey);
        this.errorKey = errorKey;
    }
    public String getErrorKey() { return errorKey; }
}

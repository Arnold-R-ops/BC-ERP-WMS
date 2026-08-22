package com.wms.system.platform.dto;

import java.util.List;

public record PlatformLoginResponse(
    String token,
    String tokenType,
    String email,
    List<String> roles,
    long expiresIn
) {
    /** Keep the bearer credential out of Spring Web and diagnostic logs. */
    @Override
    public String toString() {
        return "PlatformLoginResponse[token=[REDACTED], tokenType=" + tokenType
            + ", email=" + email + ", roles=" + roles + ", expiresIn=" + expiresIn + "]";
    }
}

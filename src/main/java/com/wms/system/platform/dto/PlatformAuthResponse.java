package com.wms.system.platform.dto;

import java.util.List;

public record PlatformAuthResponse(
    String status,
    String challengeToken,
    String enrollmentSecret,
    String otpauthUri,
    String token,
    String tokenType,
    String email,
    List<String> roles,
    long expiresIn,
    List<String> recoveryCodes
) {
    @Override public String toString() {
        return "PlatformAuthResponse[status=" + status + ", challengeToken=[REDACTED], "
            + "enrollmentSecret=[REDACTED], otpauthUri=[REDACTED], token=[REDACTED], tokenType="
            + tokenType + ", email=" + email + ", roles=" + roles + ", expiresIn=" + expiresIn
            + ", recoveryCodes=[REDACTED]]";
    }
}

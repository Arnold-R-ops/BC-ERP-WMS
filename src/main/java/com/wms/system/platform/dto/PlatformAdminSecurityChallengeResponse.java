package com.wms.system.platform.dto;

public record PlatformAdminSecurityChallengeResponse(
    String challengeToken,
    long expiresIn,
    long targetSecurityVersion
) {
    @Override
    public String toString() {
        return "PlatformAdminSecurityChallengeResponse[challengeToken=<redacted>, expiresIn="
            + expiresIn + ", targetSecurityVersion=" + targetSecurityVersion + "]";
    }
}

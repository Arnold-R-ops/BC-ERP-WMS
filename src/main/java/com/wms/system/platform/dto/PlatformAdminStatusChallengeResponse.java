package com.wms.system.platform.dto;

public record PlatformAdminStatusChallengeResponse(
    String challengeToken,
    long expiresIn,
    long targetSecurityVersion
) {
    @Override
    public String toString() {
        return "PlatformAdminStatusChallengeResponse[challengeToken=<redacted>, expiresIn="
            + expiresIn + ", targetSecurityVersion=" + targetSecurityVersion + "]";
    }
}

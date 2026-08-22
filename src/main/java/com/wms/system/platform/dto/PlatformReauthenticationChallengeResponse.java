package com.wms.system.platform.dto;

public record PlatformReauthenticationChallengeResponse(
    String challengeToken,
    long expiresIn
) {
    @Override
    public String toString() {
        return "PlatformReauthenticationChallengeResponse[challengeToken=<redacted>, expiresIn=" + expiresIn + "]";
    }
}

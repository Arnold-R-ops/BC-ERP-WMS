package com.wms.system.platform.dto;

import java.util.List;

public record PlatformRecoveryCodesResponse(List<String> recoveryCodes, int remaining) {
    public PlatformRecoveryCodesResponse {
        recoveryCodes = List.copyOf(recoveryCodes);
    }

    @Override
    public String toString() {
        return "PlatformRecoveryCodesResponse[recoveryCodes=<redacted>, remaining=" + remaining + "]";
    }
}

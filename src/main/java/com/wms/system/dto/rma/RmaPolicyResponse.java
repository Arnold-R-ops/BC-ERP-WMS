package com.wms.system.dto.rma;

import com.wms.system.entity.enums.RmaApprovalMode;

public record RmaPolicyResponse(
    RmaApprovalMode approvalMode,
    int standardReturnDays,
    boolean refundOnlyEnabled,
    boolean advanceResolutionEnabled,
    boolean quarantineStagingEnabled
) { }

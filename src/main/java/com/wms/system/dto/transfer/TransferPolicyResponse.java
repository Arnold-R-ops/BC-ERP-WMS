package com.wms.system.dto.transfer;

import com.wms.system.entity.enums.TransferApprovalMode;

public record TransferPolicyResponse(
    TransferApprovalMode approvalMode,
    boolean receivingInspectionEnabled
) { }

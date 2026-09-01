package com.wms.system.dto.transfer;

import com.wms.system.entity.enums.TransferApprovalMode;
import jakarta.validation.constraints.*;

public record UpdateTransferPolicyRequest(
    @NotNull TransferApprovalMode approvalMode,
    boolean receivingInspectionEnabled,
    @NotBlank @Size(max = 500) String reason
) { }

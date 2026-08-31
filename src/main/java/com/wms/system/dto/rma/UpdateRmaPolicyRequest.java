package com.wms.system.dto.rma;

import com.wms.system.entity.enums.RmaApprovalMode;
import jakarta.validation.constraints.*;

public record UpdateRmaPolicyRequest(
    @NotNull RmaApprovalMode approvalMode,
    @NotNull @Min(1) @Max(365) Integer standardReturnDays,
    boolean refundOnlyEnabled,
    boolean advanceResolutionEnabled,
    boolean quarantineStagingEnabled,
    @NotBlank @Size(max = 500) String reason
) { }

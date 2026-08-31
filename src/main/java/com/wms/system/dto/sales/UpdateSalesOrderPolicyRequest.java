package com.wms.system.dto.sales;

import com.wms.system.entity.enums.SalesApprovalMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateSalesOrderPolicyRequest(
    @NotNull SalesApprovalMode approvalMode,
    @NotNull @DecimalMin("0.00") BigDecimal approvalAmountThreshold,
    boolean detailedLogisticsEnabled,
    @NotNull @Size(min = 2, max = 500) String reason
) {
}

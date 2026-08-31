package com.wms.system.dto.sales;

import com.wms.system.entity.enums.SalesApprovalMode;

import java.math.BigDecimal;
import java.util.List;

public record SalesOrderPolicyResponse(
    SalesApprovalMode approvalMode,
    BigDecimal approvalAmountThreshold,
    boolean detailedLogisticsEnabled,
    List<SalesApprovalMode> approvalModes
) {
}

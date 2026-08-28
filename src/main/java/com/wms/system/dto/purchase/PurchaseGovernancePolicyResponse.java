package com.wms.system.dto.purchase;

import com.wms.system.entity.enums.PurchaseApprovalMode;
import com.wms.system.entity.enums.ShortReceiptClosureMode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PurchaseGovernancePolicyResponse {
    private PurchaseApprovalMode configuredPurchaseApprovalMode;
    private PurchaseApprovalMode effectivePurchaseApprovalMode;
    private ShortReceiptClosureMode configuredShortReceiptClosureMode;
    private ShortReceiptClosureMode effectiveShortReceiptClosureMode;
    private boolean approvalEntitled;
    private List<PurchaseApprovalMode> availablePurchaseApprovalModes;
    private List<ShortReceiptClosureMode> availableShortReceiptClosureModes;
}

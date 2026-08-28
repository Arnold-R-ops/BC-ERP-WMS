package com.wms.system.dto.purchase;

import com.wms.system.entity.PurchaseOrder;
import com.wms.system.entity.enums.PurchaseOrderStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PurchaseOrderWorkflowResponse {
    private Long id;
    private Long version;
    private String poNumber;
    private PurchaseOrderStatus status;

    public static PurchaseOrderWorkflowResponse from(PurchaseOrder order) {
        return PurchaseOrderWorkflowResponse.builder()
            .id(order.getId())
            .version(order.getVersion())
            .poNumber(order.getPoNumber())
            .status(order.getStatus())
            .build();
    }
}

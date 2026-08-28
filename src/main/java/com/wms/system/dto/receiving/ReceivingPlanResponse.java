package com.wms.system.dto.receiving;

import com.wms.system.entity.enums.ReceivingPlanStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record ReceivingPlanResponse(
    Long id,
    Long version,
    String planNumber,
    Long purchaseOrderId,
    String purchaseOrderNumber,
    Long warehouseId,
    String warehouseCode,
    String warehouseName,
    ReceivingPlanStatus status,
    Integer priority,
    LocalDateTime expectedWindowStart,
    LocalDateTime expectedWindowEnd,
    Long assignedReceiverId,
    String assignedReceiverName,
    String assignedTeam,
    String dockArea,
    String workArea,
    String warehouseNotes,
    List<Item> items
) {
    /** Warehouse-safe projection of the purchase allocation behind this plan. */
    @Schema(name = "ReceivingPlanAllocationItemResponse")
    public record Item(
        Long allocationId,
        Long purchaseOrderItemId,
        Long productSkuId,
        String skuCode,
        String productName,
        Integer plannedQuantity,
        Integer confirmedShippedQuantity,
        Integer remainingToShipQuantity
    ) {
    }
}

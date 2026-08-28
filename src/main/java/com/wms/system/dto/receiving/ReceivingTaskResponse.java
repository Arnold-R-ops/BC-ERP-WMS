package com.wms.system.dto.receiving;

import com.wms.system.entity.enums.ReceivingTaskStatus;
import com.wms.system.entity.enums.ReceivingTaskSourceType;
import com.wms.system.entity.enums.OtherInboundBusinessType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Warehouse-safe task contract. Deliberately excludes supplier identity,
 * purchase prices, shipment carrier/tracking data and the procurement ASN number.
 */
public record ReceivingTaskResponse(
    Long id,
    String taskNumber,
    ReceivingTaskSourceType sourceType,
    ReceivingTaskStatus status,
    Long purchaseOrderId,
    String purchaseOrderNumber,
    Long otherInboundApplicationId,
    String otherInboundApplicationNumber,
    OtherInboundBusinessType otherInboundBusinessType,
    String sourceReference,
    String businessReason,
    Long warehouseId,
    String warehouseCode,
    String warehouseName,
    Integer priority,
    LocalDateTime expectedWindowStart,
    LocalDateTime expectedWindowEnd,
    Long assignedReceiverId,
    String assignedReceiverName,
    String assignedTeam,
    String dockArea,
    String workArea,
    String warehouseNotes,
    LocalDateTime createdAt,
    LocalDateTime completedAt,
    LocalDateTime closedAt,
    String closeReason,
    List<Item> items
) {
    @Schema(name = "ReceivingTaskItemResponse")
    public record Item(
        Long id,
        Long productSkuId,
        String skuCode,
        String barcode,
        String productName,
        Boolean expiryManagementEnabled,
        Integer expectedQuantity,
        Integer receivedQuantity,
        Integer closedQuantity,
        Integer remainingQuantity,
        LocalDate productionDate,
        LocalDate expiryDate,
        String externalBatchCode,
        String remark
    ) {
    }
}

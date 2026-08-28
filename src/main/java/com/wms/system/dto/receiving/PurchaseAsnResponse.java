package com.wms.system.dto.receiving;

import com.wms.system.entity.enums.PurchaseAsnStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Procurement-facing ASN contract. It is never returned by warehouse task APIs. */
public record PurchaseAsnResponse(
    Long id,
    Long version,
    String asnNumber,
    Long purchaseOrderId,
    String purchaseOrderNumber,
    Long warehouseId,
    String warehouseCode,
    PurchaseAsnStatus status,
    Long correctionOfId,
    String supplierShipmentNo,
    String carrier,
    String trackingNo,
    LocalDate expectedArrivalDate,
    String remark,
    LocalDateTime confirmedAt,
    LocalDateTime closedAt,
    String closeReason,
    LocalDateTime cancelledAt,
    String cancelReason,
    List<Item> items
) {
    @Schema(name = "PurchaseAsnItemResponse")
    public record Item(
        Long id,
        Long purchaseOrderItemId,
        Long productSkuId,
        String skuCode,
        String productName,
        Integer shippedQuantity,
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

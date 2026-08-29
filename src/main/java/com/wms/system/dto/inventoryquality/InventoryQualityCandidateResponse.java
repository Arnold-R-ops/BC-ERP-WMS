package com.wms.system.dto.inventoryquality;

import com.wms.system.entity.enums.InventoryQualityStatus;

import java.time.LocalDate;

public record InventoryQualityCandidateResponse(
    Long batchId,
    String batchCode,
    String externalBatchCode,
    String qualitySegmentNo,
    Long qualityParentBatchId,
    Long productSkuId,
    String productSkuCode,
    String productName,
    Long warehouseId,
    String warehouseName,
    Long locationId,
    String locationCode,
    Integer quantity,
    Integer reservedQuantity,
    Integer availableQuantity,
    InventoryQualityStatus qualityStatus,
    LocalDate productionDate,
    LocalDate expiryDate
) {
}

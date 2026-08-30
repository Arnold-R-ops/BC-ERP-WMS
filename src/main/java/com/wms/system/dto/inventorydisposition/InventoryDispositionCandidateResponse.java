package com.wms.system.dto.inventorydisposition;

import com.wms.system.entity.enums.InventoryQualityStatus;
import java.time.LocalDate;

public record InventoryDispositionCandidateResponse(
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
    InventoryQualityStatus qualityStatus,
    LocalDate productionDate,
    LocalDate expiryDate,
    boolean dateExpired,
    Long daysUntilExpiry,
    Integer outstandingDispositionQuantity,
    Integer disposableQuantity
) { }

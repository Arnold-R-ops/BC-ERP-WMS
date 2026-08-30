package com.wms.system.dto.inventorydisposition;

import com.wms.system.entity.enums.InventoryQualityStatus;
import java.time.LocalDate;

public record InventoryDispositionLineResponse(
    Long id,
    Long sourceBatchId,
    Long resultBatchId,
    Long productSkuId,
    String productSkuCode,
    String productName,
    String sourceBatchCode,
    String resultBatchCode,
    Long warehouseId,
    String warehouseName,
    Long locationId,
    String locationCode,
    Integer quantity,
    Integer executedQuantity,
    Integer outstandingQuantity,
    Integer reallocatedReservedQuantity,
    Integer backorderedQuantity,
    Integer qualityExceptionQuantity,
    InventoryQualityStatus fromQualityStatus,
    InventoryQualityStatus toQualityStatus,
    LocalDate expiryDate
) { }

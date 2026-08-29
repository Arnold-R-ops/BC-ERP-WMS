package com.wms.system.dto.receiving;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.wms.system.entity.enums.InventoryQualityStatus;

public record ReceiptEventResponse(
    Long id,
    String eventNumber,
    Long receivingTaskId,
    String receivingTaskNumber,
    LocalDateTime receivedAt,
    String receivedByName,
    String remark,
    List<Line> lines
) {
    @Schema(name = "ReceiptEventLineResponse")
    public record Line(
        Long id,
        Long receivingTaskItemId,
        Long productSkuId,
        String skuCode,
        Long locationId,
        String locationCode,
        Long inventoryBatchId,
        String batchCode,
        Integer quantity,
        Boolean inspectionRequired,
        InventoryQualityStatus qualityStatusAtReceipt,
        LocalDate productionDate,
        LocalDate expiryDate,
        String externalBatchCode
    ) {
    }
}

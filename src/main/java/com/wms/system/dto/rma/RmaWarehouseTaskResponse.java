package com.wms.system.dto.rma;

import com.wms.system.entity.enums.RmaTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Warehouse-safe projection: no customer, sales reason, carrier or tracking fields. */
public record RmaWarehouseTaskResponse(
    Long id,
    Long version,
    String taskNumber,
    String rmaNumber,
    Long warehouseId,
    String warehouseName,
    RmaTaskStatus status,
    Integer priority,
    LocalDateTime expectedFrom,
    LocalDateTime expectedTo,
    String assignee,
    String teamName,
    String operationArea,
    String warehouseRemark,
    List<Line> lines,
    List<ReceiptLine> receiptLines
) {
    @Schema(name = "RmaWarehouseTaskLineResponse")
    public record Line(Long rmaLineId, Long productSkuId, String skuCode, String productName,
                       Integer expectedQuantity, Integer receivedQuantity) { }
    @Schema(name = "RmaWarehouseReceiptLineResponse")
    public record ReceiptLine(Long id, Long rmaLineId, Long productSkuId, String skuCode,
                              String productName, Long locationId, String locationCode,
                              Long returnBatchId, String returnBatchCode,
                              Integer quantity, Integer inspectedQuantity) { }
}

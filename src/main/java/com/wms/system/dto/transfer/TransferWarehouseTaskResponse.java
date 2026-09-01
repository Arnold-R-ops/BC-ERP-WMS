package com.wms.system.dto.transfer;

import com.wms.system.entity.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.*;
import java.util.List;

/** Warehouse-safe task projection: intentionally excludes cost, party and sales data. */
public record TransferWarehouseTaskResponse(
    Long id,
    Long version,
    String taskNumber,
    TransferTaskDirection direction,
    TransferTaskStatus status,
    int priority,
    LocalDateTime expectedFrom,
    LocalDateTime expectedTo,
    String assignee,
    String teamName,
    String operationArea,
    String warehouseRemark,
    Long transferId,
    String transferNumber,
    Long sourceWarehouseId,
    String sourceWarehouseName,
    Long destinationWarehouseId,
    String destinationWarehouseName,
    TransferBusinessStatus businessStatus,
    TransferOutboundStatus outboundStatus,
    TransferInboundStatus inboundStatus,
    LocalDateTime estimatedArrivalAt,
    List<Line> lines,
    List<DispatchLine> dispatchLines
) {
    @Schema(name = "TransferWarehouseTaskLineResponse")
    public record Line(
        Long transferLineId,
        Long productSkuId,
        String skuCode,
        String productName,
        int requestedQuantity,
        int allocatedQuantity,
        int dispatchedQuantity,
        int receivedQuantity,
        int inTransitQuantity,
        List<Allocation> allocations
    ) { }

    @Schema(name = "TransferWarehouseAllocationResponse")
    public record Allocation(
        Long allocationId,
        Long sourceBatchId,
        String sourceBatchCode,
        String externalBatchCode,
        Long sourceLocationId,
        String sourceLocationCode,
        LocalDate productionDate,
        LocalDate expiryDate,
        int remainingQuantity
    ) { }

    @Schema(name = "TransferWarehouseDispatchLineResponse")
    public record DispatchLine(
        Long dispatchLineId,
        String dispatchNumber,
        Long transferLineId,
        Long productSkuId,
        String skuCode,
        String productName,
        Long sourceBatchId,
        String sourceBatchCode,
        String externalBatchCode,
        LocalDate productionDate,
        LocalDate expiryDate,
        int dispatchedQuantity,
        int receivedQuantity,
        int openQuantity
    ) { }
}

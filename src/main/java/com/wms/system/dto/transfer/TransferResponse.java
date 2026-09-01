package com.wms.system.dto.transfer;

import com.wms.system.entity.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.*;
import java.util.List;

public record TransferResponse(
    Long id,
    Long version,
    String transferNumber,
    Long sourceWarehouseId,
    String sourceWarehouseCode,
    String sourceWarehouseName,
    Long destinationWarehouseId,
    String destinationWarehouseCode,
    String destinationWarehouseName,
    TransferBusinessStatus businessStatus,
    TransferOutboundStatus outboundStatus,
    TransferInboundStatus inboundStatus,
    TransferApprovalMode approvalModeSnapshot,
    boolean receivingInspectionSnapshot,
    String reason,
    LocalDateTime estimatedArrivalAt,
    int requestedQuantity,
    int allocatedQuantity,
    int dispatchedQuantity,
    int inTransitQuantity,
    int receivedQuantity,
    int damagedQuantity,
    int quarantinedQuantity,
    int closedQuantity,
    Long requestedBy,
    String requestedByName,
    LocalDateTime submittedAt,
    Long reviewedBy,
    String reviewedByName,
    LocalDateTime reviewedAt,
    String reviewComment,
    LocalDateTime terminatedAt,
    String terminationReason,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<Line> lines,
    List<Dispatch> dispatches,
    List<Receipt> receipts,
    List<Event> events
) {
    @Schema(name = "TransferOrderLineResponse")
    public record Line(
        Long id,
        Long version,
        Long productSkuId,
        String skuCode,
        String productName,
        Long preferredBatchId,
        Long backorderLineId,
        int requestedQuantity,
        int allocatedQuantity,
        int dispatchedQuantity,
        int inTransitQuantity,
        int receivedQuantity,
        int damagedQuantity,
        int quarantinedQuantity,
        int closedUnshippedQuantity,
        int shortageClosedQuantity,
        List<Allocation> allocations
    ) { }

    @Schema(name = "TransferAllocationResponse")
    public record Allocation(
        Long id,
        Long sourceBatchId,
        String batchCode,
        String externalBatchCode,
        Long locationId,
        String locationCode,
        int allocatedQuantity,
        int consumedQuantity,
        int releasedQuantity,
        int remainingQuantity,
        TransferAllocationStatus status,
        LocalDate productionDate,
        LocalDate expiryDate
    ) { }

    @Schema(name = "TransferDispatchResponse")
    public record Dispatch(
        Long id,
        String dispatchNumber,
        String handoverReference,
        LocalDateTime estimatedArrivalAt,
        String warehouseRemark,
        Long dispatchedBy,
        String dispatchedByName,
        LocalDateTime dispatchedAt,
        List<DispatchLine> lines
    ) { }

    @Schema(name = "TransferDispatchLineResponse")
    public record DispatchLine(
        Long id,
        Long transferLineId,
        Long allocationId,
        Long sourceBatchId,
        String sourceBatchCode,
        String externalBatchCode,
        LocalDate productionDate,
        LocalDate expiryDate,
        int quantity,
        int receivedQuantity,
        int damagedQuantity,
        int quarantinedQuantity,
        int shortageClosedQuantity,
        int openQuantity
    ) { }

    @Schema(name = "TransferReceiptResponse")
    public record Receipt(
        Long id,
        String receiptNumber,
        Long receivedBy,
        String receivedByName,
        LocalDateTime receivedAt,
        String remark,
        List<ReceiptLine> lines
    ) { }

    @Schema(name = "TransferReceiptLineResponse")
    public record ReceiptLine(
        Long id,
        Long dispatchLineId,
        Long transferLineId,
        Long locationId,
        String locationCode,
        Long destinationBatchId,
        String destinationBatchCode,
        TransferReceiptDisposition disposition,
        int quantity
    ) { }

    @Schema(name = "TransferEventResponse")
    public record Event(
        Long id,
        String eventType,
        String payloadJson,
        Long actorId,
        String actorName,
        LocalDateTime occurredAt
    ) { }
}

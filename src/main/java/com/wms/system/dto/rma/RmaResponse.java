package com.wms.system.dto.rma;

import com.wms.system.entity.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RmaResponse(
    Long id,
    Long version,
    String rmaNumber,
    Long salesOrderId,
    String salesOrderNo,
    Long shipmentId,
    Long warehouseId,
    String warehouseName,
    Long customerId,
    String customerName,
    RmaType rmaType,
    RmaBusinessStatus businessStatus,
    RmaReceiptStatus receiptStatus,
    RmaInspectionStatus inspectionStatus,
    RmaResolutionStatus resolutionStatus,
    RmaApprovalMode approvalModeSnapshot,
    CustomerType customerTypeSnapshot,
    RmaStatutoryBasis statutoryBasis,
    Integer standardReturnDaysSnapshot,
    boolean outsideStandardWindow,
    String reasonCode,
    String reasonDetail,
    LocalDateTime customerShippedAt,
    String returnCarrier,
    String returnTrackingNo,
    BigDecimal refundAmount,
    String refundCurrency,
    String refundReference,
    boolean advanceResolution,
    Long replacementSalesOrderId,
    String channel,
    String externalCaseId,
    Long requestedBy,
    String requestedByName,
    LocalDateTime requestedAt,
    Long reviewedBy,
    String reviewedByName,
    LocalDateTime reviewedAt,
    String reviewComment,
    List<Line> lines,
    Task task,
    List<Receipt> receipts,
    List<Inspection> inspections,
    List<Event> events
) {
    @Schema(name = "RmaLineResponse")
    public record Line(
        Long id,
        Long salesOrderItemId,
        Long outboundTaskId,
        Long originalBatchId,
        String originalBatchCode,
        Long productSkuId,
        String skuCode,
        String productName,
        Integer shippedQuantity,
        Integer requestedQuantity,
        Integer authorizedQuantity,
        Integer receivedQuantity,
        Integer inspectedQuantity,
        Integer restockQuantity,
        Integer damagedQuantity,
        Integer quarantineQuantity,
        Integer rejectedQuantity,
        Integer refundEligibleQuantity,
        BigDecimal unitPriceSnapshot
    ) { }

    @Schema(name = "RmaTaskResponse")
    public record Task(
        Long id,
        Long version,
        String taskNumber,
        RmaTaskStatus status,
        Integer priority,
        LocalDateTime expectedFrom,
        LocalDateTime expectedTo,
        String assignee,
        String teamName,
        String operationArea,
        String warehouseRemark
    ) { }

    @Schema(name = "RmaReceiptResponse")
    public record Receipt(
        Long id,
        String receiptNumber,
        Long receivedBy,
        String receivedByName,
        LocalDateTime receivedAt,
        String remark,
        List<ReceiptLine> lines
    ) { }

    @Schema(name = "RmaReceiptLineResponse")
    public record ReceiptLine(
        Long id,
        Long rmaLineId,
        Long locationId,
        String locationCode,
        Long returnBatchId,
        String returnBatchCode,
        InventoryQualityStatus qualityStatus,
        Integer quantity,
        Integer inspectedQuantity
    ) { }

    @Schema(name = "RmaInspectionResponse")
    public record Inspection(
        Long id,
        String inspectionNumber,
        Long inspectedBy,
        String inspectedByName,
        LocalDateTime inspectedAt,
        String reasonDetail,
        List<InspectionLine> lines
    ) { }

    @Schema(name = "RmaInspectionLineResponse")
    public record InspectionLine(
        Long id,
        Long receiptLineId,
        RmaDisposition disposition,
        Integer quantity,
        Integer refundEligibleQuantity,
        Long resultBatchId,
        String resultBatchCode,
        InventoryQualityStatus resultQualityStatus
    ) { }

    @Schema(name = "RmaEventResponse")
    public record Event(Long id, String eventType, String payloadJson, Long actorId,
                        String actorName, LocalDateTime occurredAt) { }
}

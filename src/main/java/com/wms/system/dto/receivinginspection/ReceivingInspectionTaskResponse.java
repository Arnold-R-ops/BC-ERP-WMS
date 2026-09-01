package com.wms.system.dto.receivinginspection;

import com.wms.system.entity.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record ReceivingInspectionTaskResponse(
    Long id,
    String taskNumber,
    ReceivingInspectionSourceType sourceType,
    String sourceReference,
    Long warehouseId,
    String warehouseCode,
    String warehouseName,
    ReceivingInspectionTaskStatus status,
    ReceivingInspectionReviewMode reviewMode,
    LocalDateTime createdAt,
    LocalDateTime completedAt,
    Long correctionOfResultId,
    List<Line> lines,
    List<ReceivingInspectionResultResponse> results
) {
    @Schema(name = "ReceivingInspectionTaskLineResponse")
    public record Line(
        Long id,
        Long productSkuId,
        String skuCode,
        String productName,
        Long sourceBatchId,
        String batchCode,
        Long locationId,
        String locationCode,
        Integer plannedQuantity,
        Integer pendingQuantity,
        Integer pendingReviewQuantity,
        Integer passedQuantity,
        Integer quarantinedQuantity
    ) {
    }
}

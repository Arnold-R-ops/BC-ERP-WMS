package com.wms.system.dto.receivinginspection;

import com.wms.system.entity.enums.*;

import java.time.LocalDateTime;
import java.util.List;

public record ReceivingInspectionResultResponse(
    Long id,
    String resultNumber,
    Long inspectionTaskId,
    ReceivingInspectionResultStatus status,
    ReceivingInspectionMethod inspectionMethod,
    Integer sampleQuantity,
    Integer defectQuantity,
    String reasonCode,
    String reasonDetail,
    Long submittedBy,
    String submittedByName,
    LocalDateTime submittedAt,
    Long reviewedBy,
    String reviewedByName,
    LocalDateTime reviewedAt,
    String reviewComment,
    List<Line> lines
) {
    public record Line(
        Long id,
        Long inspectionTaskLineId,
        Long sourceBatchId,
        Integer passedQuantity,
        Integer quarantinedQuantity,
        Long passedBatchId,
        Long quarantinedBatchId
    ) {
    }
}

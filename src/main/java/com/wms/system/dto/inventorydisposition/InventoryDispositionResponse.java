package com.wms.system.dto.inventorydisposition;

import com.wms.system.entity.enums.*;
import java.time.LocalDateTime;
import java.util.List;

public record InventoryDispositionResponse(
    Long id,
    String dispositionNumber,
    InventoryDispositionType dispositionType,
    InventoryDispositionStatus status,
    InventoryDispositionReviewMode reviewModeSnapshot,
    String reasonCode,
    String reasonDetail,
    String externalReference,
    String recallNoticeNumber,
    Long requestedBy,
    String requestedByName,
    LocalDateTime requestedAt,
    Long reviewedBy,
    String reviewedByName,
    LocalDateTime reviewedAt,
    String reviewComment,
    Long cancelledBy,
    String cancelledByName,
    LocalDateTime cancelledAt,
    String cancelReason,
    List<InventoryDispositionLineResponse> lines,
    List<InventoryDispositionExecutionResponse> executions
) { }

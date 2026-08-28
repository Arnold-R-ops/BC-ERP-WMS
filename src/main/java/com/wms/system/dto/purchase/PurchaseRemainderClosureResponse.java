package com.wms.system.dto.purchase;

import com.wms.system.entity.enums.PurchaseRemainderClosureStatus;

import java.time.LocalDateTime;

public record PurchaseRemainderClosureResponse(
    Long id,
    Long version,
    Long purchaseAsnId,
    String asnNumber,
    Long purchaseOrderId,
    String purchaseOrderNumber,
    Long warehouseId,
    PurchaseRemainderClosureStatus status,
    String reason,
    Long requestedBy,
    String requestedByName,
    LocalDateTime requestedAt,
    Long reviewedBy,
    String reviewedByName,
    LocalDateTime reviewedAt,
    String reviewComment
) {
}

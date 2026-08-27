package com.wms.system.service.inventory;

import com.wms.system.entity.enums.SourceType;

/**
 * Server-controlled audit context attached to one inventory posting.
 *
 * <p>The caller must derive these values from the authenticated tenant and the
 * approved business document. None of them may be accepted as an unrestricted
 * public inventory-adjustment payload.</p>
 */
public record PostingContext(
    Long companyId,
    Long warehouseId,
    SourceType sourceType,
    String sourceOrderId,
    String reasonCode,
    String remarks,
    Long operatorId,
    String operatorName,
    String postingId,
    String idempotencyKey
) {
}

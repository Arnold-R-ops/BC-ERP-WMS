package com.wms.system.dto.inventoryquality;

import com.wms.system.entity.enums.InventoryQualityActionStatus;
import com.wms.system.entity.enums.InventoryQualityActionType;
import com.wms.system.entity.enums.InventoryQualityHoldType;
import com.wms.system.entity.enums.InventoryQualityReleaseMode;

import java.time.LocalDateTime;
import java.util.List;

public record InventoryQualityActionResponse(
    Long id,
    Integer version,
    String actionNumber,
    InventoryQualityActionType actionType,
    InventoryQualityHoldType holdType,
    InventoryQualityActionStatus status,
    InventoryQualityReleaseMode releaseModeSnapshot,
    String reasonCode,
    String reasonDetail,
    String recallNoticeNumber,
    String externalReference,
    Long requestedBy,
    String requestedByName,
    LocalDateTime requestedAt,
    Long reviewedBy,
    String reviewedByName,
    LocalDateTime reviewedAt,
    String reviewComment,
    List<InventoryQualityActionLineResponse> lines
) {
}

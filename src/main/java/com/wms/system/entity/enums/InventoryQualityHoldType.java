package com.wms.system.entity.enums;

import com.wms.system.entity.enums.InventoryQualityStatus;

public enum InventoryQualityHoldType {
    QUARANTINE(InventoryQualityStatus.QUARANTINED),
    RECALL(InventoryQualityStatus.RECALL_HOLD);

    private final InventoryQualityStatus targetStatus;

    InventoryQualityHoldType(InventoryQualityStatus targetStatus) {
        this.targetStatus = targetStatus;
    }

    public InventoryQualityStatus targetStatus() {
        return targetStatus;
    }
}

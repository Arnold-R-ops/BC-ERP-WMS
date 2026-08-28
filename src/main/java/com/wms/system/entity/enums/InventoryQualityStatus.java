package com.wms.system.entity.enums;

/**
 * Authoritative physical-inventory quality state.
 *
 * <p>The state is deliberately independent from {@code InventoryBatch.active}:
 * active describes whether the physical stock row is live, while this value
 * determines whether that stock is commercially saleable.</p>
 */
public enum InventoryQualityStatus {
    AVAILABLE,
    PENDING_INSPECTION,
    QUARANTINED,
    DAMAGED,
    EXPIRED,
    RECALL_HOLD,
    RETURN_INSPECTION
}

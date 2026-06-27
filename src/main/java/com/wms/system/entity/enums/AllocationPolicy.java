package com.wms.system.entity.enums;

/**
 * Allocation behavior for sales order approval.
 */
public enum AllocationPolicy {
    /**
     * All requested quantity must be reserved, otherwise approval fails.
     */
    FULL_ONLY,

    /**
     * Reserve what is available and mark the remaining quantity as backorder.
     * The initial V4.5 foundation stores this value but still defaults to FULL_ONLY.
     */
    PARTIAL_BACKORDER,

    /**
     * Do not allocate a partial shipment. Keep the full demand as a backorder
     * until enough inventory becomes available.
     */
    WAIT_FOR_COMPLETE
}

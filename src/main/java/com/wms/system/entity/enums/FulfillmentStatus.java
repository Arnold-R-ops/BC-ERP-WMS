package com.wms.system.entity.enums;

/**
 * Warehouse fulfillment lifecycle for a sales order or order line.
 */
public enum FulfillmentStatus {
    UNALLOCATED("Unallocated"),
    RESERVED("Reserved"),
    PARTIALLY_ALLOCATED("Partially allocated"),
    WAITING_INBOUND("Waiting inbound"),
    PARTIALLY_SHIPPED("Partially shipped"),
    SHIPPED("Shipped"),
    BACKORDERED("Backordered"),
    CANCELLED("Cancelled"),
    VOIDED("Voided");

    private final String description;

    FulfillmentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

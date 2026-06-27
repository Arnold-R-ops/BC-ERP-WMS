package com.wms.system.entity.enums;

/**
 * Business source type for inventory transactions.
 */
public enum SourceType {
    PURCHASE_IN("Purchase inbound"),
    INBOUND_IN("Inbound order receipt"),
    SALE_OUT("Sales outbound"),
    RETURN_IN("Return inbound"),
    PRODUCTION_OUT("Production issue"),
    PRODUCTION_IN("Production receipt"),
    TRANSFER_OUT("Transfer out"),
    TRANSFER_IN("Transfer in"),
    INVENTORY_GAIN("Inventory gain"),
    INVENTORY_LOSS("Inventory loss"),
    SCRAP_OUT("Scrap outbound"),
    GIFT_OUT("Gift outbound"),
    SAMPLE_OUT("Sample outbound"),
    MANUAL_ADJUST("Manual adjustment"),
    EMERGENCY_CORRECTION("Emergency correction");

    private final String description;

    SourceType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isInbound() {
        return this == PURCHASE_IN
            || this == INBOUND_IN
            || this == RETURN_IN
            || this == PRODUCTION_IN
            || this == TRANSFER_IN
            || this == INVENTORY_GAIN
            || this == EMERGENCY_CORRECTION;
    }

    public boolean isOutbound() {
        return this == SALE_OUT
            || this == PRODUCTION_OUT
            || this == TRANSFER_OUT
            || this == INVENTORY_LOSS
            || this == SCRAP_OUT
            || this == GIFT_OUT
            || this == SAMPLE_OUT;
    }
}

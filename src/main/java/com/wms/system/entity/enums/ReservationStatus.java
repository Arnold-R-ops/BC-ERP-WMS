package com.wms.system.entity.enums;

/**
 * Status of an inventory reservation ledger row.
 */
public enum ReservationStatus {
    ACTIVE("有效预占"),
    PARTIALLY_CONSUMED("部分消耗"),
    CONSUMED("已消耗"),
    RELEASED("已释放"),
    EXPIRED("已过期");

    private final String description;

    ReservationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

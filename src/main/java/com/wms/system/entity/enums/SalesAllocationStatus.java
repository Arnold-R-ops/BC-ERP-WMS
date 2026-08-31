package com.wms.system.entity.enums;

/** Inventory commitment state derived from reservations and replenishment facts. */
public enum SalesAllocationStatus {
    UNALLOCATED("未分配"),
    FULLY_ALLOCATED("全部分配"),
    PARTIALLY_ALLOCATED("部分分配"),
    BACKORDERED("欠货"),
    WAITING_INBOUND("等待入库");

    private final String description;

    SalesAllocationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

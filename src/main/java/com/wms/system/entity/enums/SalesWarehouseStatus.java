package com.wms.system.entity.enums;

/** Warehouse progress derived from outbound execution facts. */
public enum SalesWarehouseStatus {
    NOT_RELEASED("未下发"),
    PENDING_PICK("待拣货"),
    PICKING("拣货中"),
    PICKED("已拣货"),
    PENDING_CHECK("待复核"),
    PACKING("包装中"),
    READY_TO_HANDOVER("待交运"),
    PARTIALLY_SHIPPED("部分发货"),
    SHIPPED("已发货"),
    EXCEPTION("异常");

    private final String description;

    SalesWarehouseStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasStartedPicking() {
        return this != NOT_RELEASED && this != PENDING_PICK;
    }
}

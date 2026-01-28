package com.wms.system.entity.enums;

/**
 * 库存预警状态
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.6
 */
public enum StockStatus {
    SUFFICIENT("充足", "green"),
    LOW_STOCK("库存不足", "red");

    private final String description;
    private final String color;

    StockStatus(String description, String color) {
        this.description = description;
        this.color = color;
    }

    public String getDescription() {
        return description;
    }

    public String getColor() {
        return color;
    }
}

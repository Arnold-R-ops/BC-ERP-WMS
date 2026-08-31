package com.wms.system.entity.enums;

/** Carrier-facing progress, independent from warehouse shipment completion. */
public enum SalesLogisticsStatus {
    NOT_CREATED("未创建运单"),
    LABEL_CREATED("已登记运单"),
    HANDED_TO_CARRIER("已交承运商"),
    IN_TRANSIT("运输中"),
    DELIVERED("已签收"),
    EXCEPTION("运输异常"),
    RETURNED("已退回");

    private final String description;

    SalesLogisticsStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

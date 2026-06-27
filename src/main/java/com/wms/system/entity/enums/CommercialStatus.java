package com.wms.system.entity.enums;

/**
 * Commercial lifecycle of a sales order.
 *
 * This status answers whether the business intent is accepted, rejected,
 * cancelled, or voided. It deliberately does not describe warehouse progress.
 */
public enum CommercialStatus {
    DRAFT("草稿"),
    PENDING_APPROVAL("待审批"),
    APPROVED("已审批"),
    REJECTED("已驳回"),
    CANCELLED("已取消"),
    VOIDED("已作废");

    private final String description;

    CommercialStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

package com.wms.system.entity.enums;

/**
 * Commercial purchase lifecycle. Shipment and warehouse execution are kept in
 * PurchaseAsn and ReceivingTask rather than folded into one ambiguous status.
 */
public enum PurchaseOrderStatus {
    DRAFT("草稿"),
    PENDING_APPROVAL("待审批"),
    REJECTED("已驳回"),
    AWAITING_SHIPMENT("待供应商发货"),
    PARTIALLY_SHIPPED("部分发货"),
    IN_TRANSIT("运输中"),
    PARTIALLY_RECEIVED("部分收货"),
    COMPLETED("已完成"),
    CLOSED("已关闭"),
    CANCELLED("已取消"),
    VOIDED("已作废");

    private final String description;

    PurchaseOrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEditableDraft() {
        return this == DRAFT || this == REJECTED;
    }

    public boolean canSubmit() {
        return isEditableDraft();
    }

    public boolean canApprove() {
        return this == PENDING_APPROVAL;
    }

    public boolean canWithdrawBeforeShipment() {
        return this == PENDING_APPROVAL || this == AWAITING_SHIPMENT
            || this == REJECTED;
    }

    public boolean canConfirmShipment() {
        return this == AWAITING_SHIPMENT || this == PARTIALLY_SHIPPED
            || this == IN_TRANSIT || this == PARTIALLY_RECEIVED;
    }

    public boolean canReceive() {
        return this == PARTIALLY_SHIPPED || this == IN_TRANSIT
            || this == PARTIALLY_RECEIVED;
    }

    public boolean hasPhysicalReceipt() {
        return this == PARTIALLY_RECEIVED || this == COMPLETED || this == CLOSED;
    }

    public boolean canCancelWithoutReceipt() {
        return !hasPhysicalReceipt() && !isFinalized();
    }

    public boolean canVoidWithoutDownstream() {
        return isEditableDraft();
    }

    public boolean canCloseRemaining() {
        return this == PARTIALLY_SHIPPED || this == IN_TRANSIT
            || this == PARTIALLY_RECEIVED;
    }

    public boolean isFinalized() {
        return this == COMPLETED || this == CLOSED || this == CANCELLED || this == VOIDED;
    }

    public boolean canPhysicallyDelete() {
        return this == DRAFT;
    }

}

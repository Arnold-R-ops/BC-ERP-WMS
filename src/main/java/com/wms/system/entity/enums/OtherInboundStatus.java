package com.wms.system.entity.enums;

public enum OtherInboundStatus {
    DRAFT,
    CONFIRMED,
    PARTIALLY_RECEIVED,
    COMPLETED,
    CLOSED,
    CANCELLED,
    VOIDED;

    public boolean hasReceipt() {
        return this == PARTIALLY_RECEIVED || this == COMPLETED || this == CLOSED;
    }

    public boolean isFinalized() {
        return this == COMPLETED || this == CLOSED || this == CANCELLED || this == VOIDED;
    }
}

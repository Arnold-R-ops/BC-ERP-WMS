package com.wms.system.entity.enums;

public enum PurchaseAsnStatus {
    DRAFT,
    CONFIRMED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CLOSED,
    CANCELLED,
    CORRECTED;

    public boolean hasReceiptHistory() {
        return this == PARTIALLY_RECEIVED || this == RECEIVED || this == CLOSED;
    }

    public boolean isTerminal() {
        return this == RECEIVED || this == CLOSED || this == CANCELLED || this == CORRECTED;
    }
}

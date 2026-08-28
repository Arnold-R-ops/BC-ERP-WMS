package com.wms.system.entity.enums;

public enum ReceivingTaskStatus {
    PENDING,
    PARTIALLY_RECEIVED,
    COMPLETED,
    CLOSED,
    CANCELLED;

    public boolean acceptsReceipts() {
        return this == PENDING || this == PARTIALLY_RECEIVED;
    }
}

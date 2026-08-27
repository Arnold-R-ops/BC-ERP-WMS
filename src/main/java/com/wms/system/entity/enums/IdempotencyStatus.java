package com.wms.system.entity.enums;

public enum IdempotencyStatus {
    PROCESSING,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}

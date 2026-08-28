package com.wms.system.entity.enums;

/** Tenant-selectable governance for purchase-order submission. */
public enum PurchaseApprovalMode {
    /** Submission makes the order effective immediately. */
    NONE,
    /** Submission requires one explicit approver. */
    SIMPLE
}

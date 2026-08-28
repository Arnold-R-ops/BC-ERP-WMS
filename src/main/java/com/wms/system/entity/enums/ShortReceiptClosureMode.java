package com.wms.system.entity.enums;

/** Governance for abandoning an unreceived purchase/ASN remainder. */
public enum ShortReceiptClosureMode {
    /** An authorized buyer closes the remainder and records a reason. */
    DIRECT_WITH_REASON,
    /** A buyer requests closure and a separate approver confirms it. */
    APPROVAL_REQUIRED
}

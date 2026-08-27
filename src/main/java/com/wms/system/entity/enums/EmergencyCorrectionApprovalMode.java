package com.wms.system.entity.enums;

/**
 * Approval policy captured on an emergency stock correction when it leaves draft.
 *
 * <p>The mode is a workflow rule, not a subscription plan name. Subscription
 * entitlements only decide whether a tenant may select an approval mode.</p>
 */
public enum EmergencyCorrectionApprovalMode {
    NONE,
    TWO_PERSON,
    THREE_PERSON
}

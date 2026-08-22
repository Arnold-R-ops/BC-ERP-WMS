package com.wms.system.signup.model;

public enum SignupStatus {
    EMAIL_PENDING,
    EMAIL_VERIFIED,
    DETAILS_COMPLETED,
    PROVISIONING,
    ACTIVE,
    PROVISIONING_FAILED,
    EXPIRED
}

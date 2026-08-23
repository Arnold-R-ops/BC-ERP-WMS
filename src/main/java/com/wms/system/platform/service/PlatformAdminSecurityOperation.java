package com.wms.system.platform.service;

enum PlatformAdminSecurityOperation {
    SESSION_REVOKE(
        "ADMIN_SESSIONS_REVOKE",
        "ADMIN_SESSIONS_REVOKED",
        "platform_admin_sessions"
    ),
    MFA_RESET(
        "ADMIN_MFA_RESET",
        "MFA_RESET",
        "platform_admin_mfa"
    );

    private final String commandAction;
    private final String auditAction;
    private final String resourceType;

    PlatformAdminSecurityOperation(String commandAction, String auditAction, String resourceType) {
        this.commandAction = commandAction;
        this.auditAction = auditAction;
        this.resourceType = resourceType;
    }

    String commandAction() {
        return commandAction;
    }

    String purpose() {
        return commandAction;
    }

    String auditAction() {
        return auditAction;
    }

    String resourceType() {
        return resourceType;
    }
}

package com.wms.system.tenant.security;

/** Server-owned purposes; never populated from a client header or request field. */
public enum TenantOperationPurpose {
    BUSINESS, AUTHENTICATION, PLATFORM_READ, WEBHOOK_INBOX, PROVISIONING, SECURITY_CONTROL
}

package com.wms.system.tenant.security;

import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class TenantSecurityFoundationTest {
    @ParameterizedTest @EnumSource(TenantStatus.class)
    void businessOnlyRunsForActiveTenant(TenantStatus state) {
        if (state == TenantStatus.ACTIVE) assertDoesNotThrow(() -> TenantAccessPolicy.require(state, TenantOperationPurpose.BUSINESS));
        else assertThrows(TenantAccessException.class, () -> TenantAccessPolicy.require(state, TenantOperationPurpose.BUSINESS));
    }
    @Test void suspendedPlatformReadIsNotTenantBusinessExport() {
        assertDoesNotThrow(() -> TenantAccessPolicy.require(TenantStatus.SUSPENDED, TenantOperationPurpose.PLATFORM_READ));
        assertThrows(TenantAccessException.class, () -> TenantAccessPolicy.require(TenantStatus.SUSPENDED, TenantOperationPurpose.BUSINESS));
        assertDoesNotThrow(() -> TenantAccessPolicy.require(TenantStatus.SUSPENDED, TenantOperationPurpose.AUTHENTICATION));
    }
    @Test void completedLifecycleNeverAllowsReadOrLogin() {
        for (var state : new TenantStatus[]{TenantStatus.CLOSED, TenantStatus.PURGE_PENDING, TenantStatus.PURGED})
            for (var purpose : TenantOperationPurpose.values())
                assertThrows(TenantAccessException.class, () -> TenantAccessPolicy.require(state, purpose));
    }
    @Test void admissionCannotAdoptANewVersionAfterResume() {
        try (var scope = TenantExecutionScope.open(11, TenantOperationPurpose.BUSINESS, 1L)) {
            scope.pin(1);
            assertThrows(TenantAccessException.class, () -> scope.pin(3));
            assertThrows(TenantAccessException.class, () -> TenantExecutionScope.open(11,TenantOperationPurpose.BUSINESS,3L));
            try(var nested=TenantExecutionScope.open(11,TenantOperationPurpose.BUSINESS,null)) {
                assertEquals(Long.valueOf(1),nested.sessionVersion());
            }
            assertThrows(IllegalStateException.class, () -> TenantExecutionScope.open(12, TenantOperationPurpose.BUSINESS, 1L));
        }
        assertNull(TenantExecutionScope.current());
    }
    @Test void scopeRestoresOnFailureAndNestedControl() {
        try (var outer = TenantExecutionScope.open(11, TenantOperationPurpose.AUTHENTICATION, null)) {
            outer.pin(2);
            try (var inner = TenantExecutionScope.open(11, TenantOperationPurpose.AUTHENTICATION, 2L)) {
                assertEquals(inner, TenantExecutionScope.current());
            }
            assertEquals(outer, TenantExecutionScope.current());
        }
        assertNull(TenantExecutionScope.current());
    }
    @Test void destructiveLifecycleIsUnreleased() {
        assertThrows(TenantAccessException.class, TenantLifecycleReleaseGate::requirePurgeReleased);
    }
}

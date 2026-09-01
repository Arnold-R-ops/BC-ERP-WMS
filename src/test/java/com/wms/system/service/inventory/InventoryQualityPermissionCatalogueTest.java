package com.wms.system.service.inventory;

import com.wms.system.entity.SysPermission;
import com.wms.system.repository.SysPermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryQualityPermissionCatalogueTest {
    @Mock private SysPermissionRepository repository;

    @Test
    void newTenantReceivesQualityInspectionDispositionRmaAndTransferCustomPermissions() {
        when(repository.existsByCompanyIdAndPermissionCode(eq(88L), anyString()))
            .thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new InventoryQualityPermissionCatalogue(repository).provisionForTenant(88L);

        ArgumentCaptor<SysPermission> captor = ArgumentCaptor.forClass(SysPermission.class);
        verify(repository, times(31)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(SysPermission::getPermissionCode)
            .containsExactly(
                "inventory_quality:view", "inventory_quality:hold",
                "inventory_quality:release", "inventory_quality:review",
                "inventory_quality:policy",
                "receiving_inspection:view", "receiving_inspection:execute",
                "receiving_inspection:review", "receiving_inspection:correct",
                "receiving_inspection:policy",
                "inventory_disposition:view", "inventory_disposition:report",
                "inventory_disposition:create", "inventory_disposition:execute",
                "inventory_disposition:review", "inventory_disposition:policy",
                "rma:view", "rma:create", "rma:approve", "rma:receive",
                "rma:inspect", "rma:resolve", "rma:policy", "rma:advance-resolution",
                "transfer:view", "transfer:create", "transfer:approve", "transfer:dispatch",
                "transfer:receive", "transfer:close", "transfer:policy"
            );
        assertThat(captor.getAllValues()).allSatisfy(permission -> {
            assertThat(permission.isCustomAssignable()).isTrue();
            assertThat(permission.getStatus()).isEqualTo("ACTIVE");
            assertThat(permission.getRiskLevel()).isNotEqualTo(SysPermission.RISK_LEVEL_CRITICAL);
        });
        assertThat(captor.getAllValues().stream()
            .filter(permission -> java.util.Set.of(
                "transfer:create", "transfer:approve", "transfer:close", "transfer:policy")
                .contains(permission.getPermissionCode())).toList())
            .hasSize(4)
            .allSatisfy(permission -> {
                assertThat(permission.getResourcePath()).isEqualTo("/api/transfers/**");
                assertThat(permission.getHttpMethod()).isEqualTo("*");
            });
        verify(repository, times(31)).existsByCompanyIdAndPermissionCode(eq(88L), anyString());
        verifyNoMoreInteractions(repository);
    }
}

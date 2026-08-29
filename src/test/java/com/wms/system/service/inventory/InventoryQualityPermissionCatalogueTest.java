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
    void newTenantReceivesQualityAndInspectionCustomPermissions() {
        when(repository.existsByCompanyIdAndPermissionCode(eq(88L), anyString()))
            .thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new InventoryQualityPermissionCatalogue(repository).provisionForTenant(88L);

        ArgumentCaptor<SysPermission> captor = ArgumentCaptor.forClass(SysPermission.class);
        verify(repository, times(10)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(SysPermission::getPermissionCode)
            .containsExactly(
                "inventory_quality:view", "inventory_quality:hold",
                "inventory_quality:release", "inventory_quality:review",
                "inventory_quality:policy",
                "receiving_inspection:view", "receiving_inspection:execute",
                "receiving_inspection:review", "receiving_inspection:correct",
                "receiving_inspection:policy"
            );
        assertThat(captor.getAllValues()).allSatisfy(permission -> {
            assertThat(permission.isCustomAssignable()).isTrue();
            assertThat(permission.getStatus()).isEqualTo("ACTIVE");
        });
        verify(repository, times(10)).existsByCompanyIdAndPermissionCode(eq(88L), anyString());
        verifyNoMoreInteractions(repository);
    }
}

package com.wms.system.service;

import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysUserWarehouse;
import com.wms.system.entity.Warehouse;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysUserWarehouseRepository;
import com.wms.system.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserWarehouseServiceTest {

    @Mock
    private SysUserWarehouseRepository userWarehouseRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @InjectMocks
    private UserWarehouseService service;

    @Test
    void warehouseStaffRequiresAtLeastOneWarehouse() {
        assertThatThrownBy(() -> service.validateSelection(List.of(staffRole()), List.of()))
            .isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.getErrorKey()).isEqualTo(ErrorKeys.WAREHOUSE_SCOPE_REQUIRED));
    }

    @Test
    void warehouseStaffRejectsInactiveWarehouse() {
        Warehouse inactive = warehouse(7L, "WH07", false);
        when(warehouseRepository.findAllById(Set.of(7L))).thenReturn(List.of(inactive));

        assertThatThrownBy(() -> service.validateSelection(List.of(staffRole()), List.of(7L)))
            .isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.getErrorKey()).isEqualTo(ErrorKeys.WAREHOUSE_INACTIVE));
    }

    @Test
    void replacingStaffAssignmentsStoresUniqueActiveWarehouses() {
        Warehouse first = warehouse(1L, "WH01", true);
        Warehouse second = warehouse(2L, "WH02", true);
        when(warehouseRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(first, second));

        service.replaceAssignments(9L, List.of(staffRole()), List.of(1L, 2L, 1L), 3L);

        verify(userWarehouseRepository).deleteByUserId(9L);
        ArgumentCaptor<List<SysUserWarehouse>> captor = ArgumentCaptor.forClass(List.class);
        verify(userWarehouseRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
            .extracting(SysUserWarehouse::getWarehouseId)
            .containsExactlyInAnyOrder(1L, 2L);
        assertThat(captor.getValue()).allMatch(assignment -> assignment.getAssignedBy().equals(3L));
    }

    @Test
    void removingStaffRoleClearsAssignmentsAndIgnoresSubmittedWarehouses() {
        SysRole nonStaff = SysRole.builder().id(4L).roleCode("WAREHOUSE_ADMIN").build();

        assertThatCode(() -> service.replaceAssignments(9L, List.of(nonStaff), List.of(1L), 3L))
            .doesNotThrowAnyException();

        verify(userWarehouseRepository).deleteByUserId(9L);
        verify(userWarehouseRepository, never()).saveAll(anyList());
        verify(warehouseRepository, never()).findAllById(anyList());
    }

    private SysRole staffRole() {
        return SysRole.builder().id(8L).roleCode(UserWarehouseService.WAREHOUSE_STAFF).build();
    }

    private Warehouse warehouse(Long id, String code, boolean active) {
        return Warehouse.builder().id(id).code(code).name(code).isActive(active).build();
    }
}

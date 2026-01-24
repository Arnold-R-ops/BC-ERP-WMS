package com.wms.system.service;

import com.wms.system.entity.Warehouse;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WarehouseService 单元测试
 *
 * 测试仓库管理服务的核心功能
 *
 * 测试场景：
 * 1. 创建仓库（成功/编码重复）
 * 2. 根据ID查询仓库（成功/不存在）
 * 3. 根据编码查询仓库（成功/不存在）
 * 4. 查询所有激活的仓库
 * 5. 查询所有仓库
 * 6. 更新仓库信息
 * 7. 激活仓库
 * 8. 停用仓库
 * 9. 获取仓库的库位数量
 *
 * @author WMS Team
 * @since 2025-01-23 (Phase 3.4)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseService 单元测试")
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private WarehouseService warehouseService;

    private Warehouse testWarehouse;

    @BeforeEach
    void setUp() {
        testWarehouse = Warehouse.builder()
                .id(1L)
                .code("WH01")
                .name("Main Warehouse")
                .address("123 Main St")
                .contact("John Doe")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("创建仓库 - 成功")
    void createWarehouse_Success() {
        // Given
        when(warehouseRepository.existsByCode("WH02")).thenReturn(false);
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse);

        // When
        Warehouse result = warehouseService.createWarehouse("WH02", "New Warehouse", "456 New St", "Jane Doe");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo("WH01");

        // Verify
        ArgumentCaptor<Warehouse> warehouseCaptor = ArgumentCaptor.forClass(Warehouse.class);
        verify(warehouseRepository).save(warehouseCaptor.capture());
        Warehouse savedWarehouse = warehouseCaptor.getValue();
        assertThat(savedWarehouse.getCode()).isEqualTo("WH02");
        assertThat(savedWarehouse.getName()).isEqualTo("New Warehouse");
        assertThat(savedWarehouse.getAddress()).isEqualTo("456 New St");
        assertThat(savedWarehouse.getContact()).isEqualTo("Jane Doe");
        assertThat(savedWarehouse.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("创建仓库 - 编码已存在")
    void createWarehouse_CodeAlreadyExists() {
        // Given
        when(warehouseRepository.existsByCode("WH01")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> warehouseService.createWarehouse("WH01", "Duplicate Warehouse", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_ALREADY_EXISTS);

        // Verify
        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    @DisplayName("根据ID查询仓库 - 成功")
    void getWarehouseById_Success() {
        // Given
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));

        // When
        Warehouse result = warehouseService.getWarehouseById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCode()).isEqualTo("WH01");
        assertThat(result.getName()).isEqualTo("Main Warehouse");
    }

    @Test
    @DisplayName("根据ID查询仓库 - 不存在")
    void getWarehouseById_NotFound() {
        // Given
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> warehouseService.getWarehouseById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);
    }

    @Test
    @DisplayName("根据编码查询仓库 - 成功")
    void getWarehouseByCode_Success() {
        // Given
        when(warehouseRepository.findByCode("WH01")).thenReturn(Optional.of(testWarehouse));

        // When
        Warehouse result = warehouseService.getWarehouseByCode("WH01");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo("WH01");
    }

    @Test
    @DisplayName("根据编码查询仓库 - 不存在")
    void getWarehouseByCode_NotFound() {
        // Given
        when(warehouseRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> warehouseService.getWarehouseByCode("INVALID"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);
    }

    @Test
    @DisplayName("查询所有激活的仓库")
    void getAllActiveWarehouses() {
        // Given
        Warehouse warehouse2 = Warehouse.builder()
                .id(2L)
                .code("WH02")
                .name("Secondary Warehouse")
                .isActive(true)
                .build();
        when(warehouseRepository.findAllActive()).thenReturn(Arrays.asList(testWarehouse, warehouse2));

        // When
        List<Warehouse> result = warehouseService.getAllActiveWarehouses();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Warehouse::getCode).containsExactly("WH01", "WH02");
    }

    @Test
    @DisplayName("查询所有仓库")
    void getAllWarehouses() {
        // Given
        Warehouse inactiveWarehouse = Warehouse.builder()
                .id(3L)
                .code("WH03")
                .name("Inactive Warehouse")
                .isActive(false)
                .build();
        when(warehouseRepository.findAll()).thenReturn(Arrays.asList(testWarehouse, inactiveWarehouse));

        // When
        List<Warehouse> result = warehouseService.getAllWarehouses();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Warehouse::getIsActive).containsExactly(true, false);
    }

    @Test
    @DisplayName("更新仓库信息 - 成功")
    void updateWarehouse_Success() {
        // Given
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse);

        // When
        Warehouse result = warehouseService.updateWarehouse(1L, "Updated Name", "Updated Address", "Updated Contact");

        // Then
        assertThat(result).isNotNull();
        verify(warehouseRepository).save(testWarehouse);
        assertThat(testWarehouse.getName()).isEqualTo("Updated Name");
        assertThat(testWarehouse.getAddress()).isEqualTo("Updated Address");
        assertThat(testWarehouse.getContact()).isEqualTo("Updated Contact");
    }

    @Test
    @DisplayName("更新仓库信息 - 仓库不存在")
    void updateWarehouse_NotFound() {
        // Given
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> warehouseService.updateWarehouse(999L, "New Name", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);

        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    @DisplayName("激活仓库 - 成功")
    void activateWarehouse_Success() {
        // Given
        testWarehouse.setIsActive(false);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse);

        // When
        Warehouse result = warehouseService.activateWarehouse(1L);

        // Then
        assertThat(result.getIsActive()).isTrue();
        verify(warehouseRepository).save(testWarehouse);
    }

    @Test
    @DisplayName("停用仓库 - 成功")
    void deactivateWarehouse_Success() {
        // Given
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse);

        // When
        Warehouse result = warehouseService.deactivateWarehouse(1L);

        // Then
        assertThat(result.getIsActive()).isFalse();
        verify(warehouseRepository).save(testWarehouse);
    }

    @Test
    @DisplayName("获取仓库的库位数量")
    void getLocationCount() {
        // Given
        when(warehouseRepository.countLocationsByWarehouseId(1L)).thenReturn(10L);

        // When
        long count = warehouseService.getLocationCount(1L);

        // Then
        assertThat(count).isEqualTo(10L);
    }
}

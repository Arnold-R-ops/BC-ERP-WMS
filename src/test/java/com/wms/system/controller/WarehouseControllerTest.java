package com.wms.system.controller;

import com.wms.system.controller.WarehouseController.CreateWarehouseRequest;
import com.wms.system.controller.WarehouseController.UpdateWarehouseRequest;
import com.wms.system.entity.Warehouse;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WarehouseController 单元测试
 *
 * 使用 Mockito 模拟所有依赖，专注于测试控制器逻辑
 *
 * 测试覆盖：
 * 1. 获取所有仓库
 * 2. 获取所有激活的仓库
 * 3. 根据ID获取仓库（成功/不存在）
 * 4. 根据编码获取仓库（成功/不存在）
 * 5. 创建仓库（成功/编码重复）
 * 6. 更新仓库（成功/不存在）
 * 7. 激活仓库
 * 8. 停用仓库
 * 9. 获取仓库的库位数量
 *
 * @author WMS Team
 * @since 2025-01-23 (Phase 3.4)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseController 单元测试")
class WarehouseControllerTest {

    @Mock
    private WarehouseService warehouseService;

    @InjectMocks
    private WarehouseController warehouseController;

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
    @DisplayName("获取所有仓库 - 成功")
    void getAllWarehouses_Success() {
        // Given
        Warehouse warehouse2 = Warehouse.builder()
                .id(2L)
                .code("WH02")
                .name("Secondary Warehouse")
                .isActive(false)
                .build();
        when(warehouseService.getAllWarehouses()).thenReturn(Arrays.asList(testWarehouse, warehouse2));

        // When
        var response = warehouseController.getAllWarehouses();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).extracting(Warehouse::getCode)
                .containsExactly("WH01", "WH02");
    }

    @Test
    @DisplayName("获取所有激活的仓库 - 成功")
    void getAllActiveWarehouses_Success() {
        // Given
        when(warehouseService.getAllActiveWarehouses()).thenReturn(Arrays.asList(testWarehouse));

        // When
        var response = warehouseController.getAllActiveWarehouses();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getIsActive()).isTrue();
    }

    @Test
    @DisplayName("根据ID获取仓库 - 成功")
    void getWarehouseById_Success() {
        // Given
        when(warehouseService.getWarehouseById(1L)).thenReturn(testWarehouse);

        // When
        var response = warehouseController.getWarehouseById(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(1L);
        assertThat(response.getBody().getCode()).isEqualTo("WH01");
    }

    @Test
    @DisplayName("根据ID获取仓库 - 不存在")
    void getWarehouseById_NotFound() {
        // Given
        when(warehouseService.getWarehouseById(999L))
                .thenThrow(new BusinessException(ErrorKeys.WAREHOUSE_NOT_FOUND));

        // When & Then
        assertThatThrownBy(() -> warehouseController.getWarehouseById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);
    }

    @Test
    @DisplayName("根据编码获取仓库 - 成功")
    void getWarehouseByCode_Success() {
        // Given
        when(warehouseService.getWarehouseByCode("WH01")).thenReturn(testWarehouse);

        // When
        var response = warehouseController.getWarehouseByCode("WH01");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("WH01");
    }

    @Test
    @DisplayName("根据编码获取仓库 - 不存在")
    void getWarehouseByCode_NotFound() {
        // Given
        when(warehouseService.getWarehouseByCode("INVALID"))
                .thenThrow(new BusinessException(ErrorKeys.WAREHOUSE_NOT_FOUND));

        // When & Then
        assertThatThrownBy(() -> warehouseController.getWarehouseByCode("INVALID"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);
    }

    @Test
    @DisplayName("创建仓库 - 成功")
    void createWarehouse_Success() {
        // Given
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "WH02",
                "New Warehouse",
                "456 New St",
                "Jane Doe"
        );
        when(warehouseService.createWarehouse(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(testWarehouse);

        // When
        var response = warehouseController.createWarehouse(request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isNotNull();
        verify(warehouseService).createWarehouse("WH02", "New Warehouse", "456 New St", "Jane Doe");
    }

    @Test
    @DisplayName("创建仓库 - 编码已存在")
    void createWarehouse_CodeAlreadyExists() {
        // Given
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "WH01",
                "Duplicate Warehouse",
                null,
                null
        );
        when(warehouseService.createWarehouse(anyString(), anyString(), any(), any()))
                .thenThrow(new BusinessException(ErrorKeys.WAREHOUSE_ALREADY_EXISTS));

        // When & Then
        assertThatThrownBy(() -> warehouseController.createWarehouse(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("更新仓库 - 成功")
    void updateWarehouse_Success() {
        // Given
        UpdateWarehouseRequest request = new UpdateWarehouseRequest(
                "Updated Name",
                "Updated Address",
                "Updated Contact"
        );
        when(warehouseService.updateWarehouse(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(testWarehouse);

        // When
        var response = warehouseController.updateWarehouse(1L, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(warehouseService).updateWarehouse(1L, "Updated Name", "Updated Address", "Updated Contact");
    }

    @Test
    @DisplayName("更新仓库 - 不存在")
    void updateWarehouse_NotFound() {
        // Given
        UpdateWarehouseRequest request = new UpdateWarehouseRequest("New Name", null, null);
        when(warehouseService.updateWarehouse(anyLong(), anyString(), any(), any()))
                .thenThrow(new BusinessException(ErrorKeys.WAREHOUSE_NOT_FOUND));

        // When & Then
        assertThatThrownBy(() -> warehouseController.updateWarehouse(999L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);
    }

    @Test
    @DisplayName("激活仓库 - 成功")
    void activateWarehouse_Success() {
        // Given
        when(warehouseService.activateWarehouse(1L)).thenReturn(testWarehouse);

        // When
        var response = warehouseController.activateWarehouse(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(warehouseService).activateWarehouse(1L);
    }

    @Test
    @DisplayName("停用仓库 - 成功")
    void deactivateWarehouse_Success() {
        // Given
        testWarehouse.setIsActive(false);
        when(warehouseService.deactivateWarehouse(1L)).thenReturn(testWarehouse);

        // When
        var response = warehouseController.deactivateWarehouse(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(warehouseService).deactivateWarehouse(1L);
    }

    @Test
    @DisplayName("获取仓库的库位数量 - 成功")
    void getLocationCount_Success() {
        // Given
        when(warehouseService.getLocationCount(1L)).thenReturn(10L);

        // When
        var response = warehouseController.getLocationCount(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEqualTo(10L);
    }
}

package com.wms.system.controller;

import com.wms.system.controller.LocationController.CreateLocationRequest;
import com.wms.system.controller.LocationController.UpdateLocationRequest;
import com.wms.system.entity.Location;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.Zone;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.service.LocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LocationController 单元测试
 *
 * 使用 Mockito 模拟所有依赖，专注于测试控制器逻辑
 *
 * 测试覆盖：
 * 1. 根据ID获取库位（成功/不存在）
 * 2. 根据仓库ID获取所有库位
 * 3. 获取指定仓库的空闲库位
 * 4. 创建库位（成功/仓库不存在/库位已存在）
 * 5. 更新库位（成功/不存在）
 * 6. 启用库位
 * 7. 禁用库位
 *
 * @author WMS Team
 * @since 2025-01-23 (Phase 3.4)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LocationController 单元测试")
class LocationControllerTest {

    @Mock
    private LocationService locationService;

    @InjectMocks
    private LocationController locationController;

    private Warehouse testWarehouse;
    private Location testLocation;

    @BeforeEach
    void setUp() {
        testWarehouse = Warehouse.builder()
                .id(1L)
                .code("WH01")
                .name("Main Warehouse")
                .isActive(true)
                .build();

        testLocation = Location.builder()
                .id(1L)
                .warehouse(testWarehouse)
                .zone(Zone.ZONE_A)
                .shelfNumber("A-01")
                .positionNumber("001")
                .enabled(true)
                .remark("Test location")
                .build();
    }

    @Test
    @DisplayName("根据ID获取库位 - 成功")
    void getLocationById_Success() {
        // Given
        when(locationService.getLocationById(1L)).thenReturn(testLocation);

        // When
        var response = locationController.getLocationById(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(1L);
        assertThat(response.getBody().getZone()).isEqualTo(Zone.ZONE_A);
    }

    @Test
    @DisplayName("根据ID获取库位 - 不存在")
    void getLocationById_NotFound() {
        // Given
        when(locationService.getLocationById(999L))
                .thenThrow(new BusinessException(ErrorKeys.LOCATION_NOT_FOUND));

        // When & Then
        assertThatThrownBy(() -> locationController.getLocationById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.LOCATION_NOT_FOUND);
    }

    @Test
    @DisplayName("根据仓库ID获取所有库位 - 成功")
    void getLocationsByWarehouse_Success() {
        // Given
        Location location2 = Location.builder()
                .id(2L)
                .warehouse(testWarehouse)
                .zone(Zone.ZONE_B)
                .shelfNumber("B-01")
                .positionNumber("001")
                .enabled(true)
                .build();
        when(locationService.getLocationsByWarehouse(1L)).thenReturn(Arrays.asList(testLocation, location2));

        // When
        var response = locationController.getLocationsByWarehouse(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).extracting(Location::getZone)
                .containsExactly(Zone.ZONE_A, Zone.ZONE_B);
    }

    @Test
    @DisplayName("获取指定仓库的空闲库位 - 成功")
    void getEmptyLocationsByWarehouse_Success() {
        // Given
        when(locationService.getEmptyLocationsByWarehouse(1L)).thenReturn(Arrays.asList(testLocation));

        // When
        var response = locationController.getEmptyLocationsByWarehouse(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("创建库位 - 成功")
    void createLocation_Success() {
        // Given
        CreateLocationRequest request = new CreateLocationRequest(
                1L,
                Zone.ZONE_A,
                "A-01",
                "001",
                "Test location"
        );
        when(locationService.createLocation(anyLong(), any(Zone.class), anyString(), anyString(), anyString()))
                .thenReturn(testLocation);

        // When
        var response = locationController.createLocation(request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isNotNull();
        verify(locationService).createLocation(1L, Zone.ZONE_A, "A-01", "001", "Test location");
    }

    @Test
    @DisplayName("创建库位 - 仓库不存在")
    void createLocation_WarehouseNotFound() {
        // Given
        CreateLocationRequest request = new CreateLocationRequest(
                999L,
                Zone.ZONE_A,
                "A-01",
                "001",
                null
        );
        when(locationService.createLocation(anyLong(), any(Zone.class), anyString(), anyString(), any()))
                .thenThrow(new BusinessException(ErrorKeys.WAREHOUSE_NOT_FOUND));

        // When & Then
        assertThatThrownBy(() -> locationController.createLocation(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);
    }

    @Test
    @DisplayName("创建库位 - 库位已存在")
    void createLocation_LocationAlreadyExists() {
        // Given
        CreateLocationRequest request = new CreateLocationRequest(
                1L,
                Zone.ZONE_A,
                "A-01",
                "001",
                null
        );
        when(locationService.createLocation(anyLong(), any(Zone.class), anyString(), anyString(), any()))
                .thenThrow(new BusinessException(ErrorKeys.LOCATION_ALREADY_EXISTS));

        // When & Then
        assertThatThrownBy(() -> locationController.createLocation(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.LOCATION_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("更新库位 - 成功")
    void updateLocation_Success() {
        // Given
        UpdateLocationRequest request = new UpdateLocationRequest("Updated remark");
        when(locationService.updateLocation(anyLong(), anyString())).thenReturn(testLocation);

        // When
        var response = locationController.updateLocation(1L, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(locationService).updateLocation(1L, "Updated remark");
    }

    @Test
    @DisplayName("更新库位 - 不存在")
    void updateLocation_NotFound() {
        // Given
        UpdateLocationRequest request = new UpdateLocationRequest("New remark");
        when(locationService.updateLocation(anyLong(), anyString()))
                .thenThrow(new BusinessException(ErrorKeys.LOCATION_NOT_FOUND));

        // When & Then
        assertThatThrownBy(() -> locationController.updateLocation(999L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.LOCATION_NOT_FOUND);
    }

    @Test
    @DisplayName("启用库位 - 成功")
    void enableLocation_Success() {
        // Given
        when(locationService.enableLocation(1L)).thenReturn(testLocation);

        // When
        var response = locationController.enableLocation(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(locationService).enableLocation(1L);
    }

    @Test
    @DisplayName("禁用库位 - 成功")
    void disableLocation_Success() {
        // Given
        testLocation.setEnabled(false);
        when(locationService.disableLocation(1L)).thenReturn(testLocation);

        // When
        var response = locationController.disableLocation(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(locationService).disableLocation(1L);
    }
}

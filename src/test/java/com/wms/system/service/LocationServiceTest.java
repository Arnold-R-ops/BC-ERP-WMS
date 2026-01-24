package com.wms.system.service;

import com.wms.system.entity.Location;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.Zone;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.LocationRepository;
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
 * LocationService 单元测试
 *
 * 测试库位管理服务的核心功能
 *
 * 测试场景：
 * 1. 创建库位（成功/仓库不存在/库位已存在）
 * 2. 根据ID查询库位（成功/不存在）
 * 3. 根据仓库ID查询库位
 * 4. 查询仓库的空闲库位
 * 5. 更新库位信息
 * 6. 启用库位
 * 7. 禁用库位
 *
 * @author WMS Team
 * @since 2025-01-23 (Phase 3.4)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LocationService 单元测试")
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private LocationService locationService;

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
    @DisplayName("创建库位 - 成功")
    void createLocation_Success() {
        // Given
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));
        when(locationRepository.findByWarehouseCodeAndZoneAndShelfNumberAndPositionNumber(
                "WH01", Zone.ZONE_A, "A-01", "001"
        )).thenReturn(Optional.empty());
        when(locationRepository.save(any(Location.class))).thenReturn(testLocation);

        // When
        Location result = locationService.createLocation(1L, Zone.ZONE_A, "A-01", "001", "Test location");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getZone()).isEqualTo(Zone.ZONE_A);

        // Verify
        ArgumentCaptor<Location> locationCaptor = ArgumentCaptor.forClass(Location.class);
        verify(locationRepository).save(locationCaptor.capture());
        Location savedLocation = locationCaptor.getValue();
        assertThat(savedLocation.getWarehouse()).isEqualTo(testWarehouse);
        assertThat(savedLocation.getZone()).isEqualTo(Zone.ZONE_A);
        assertThat(savedLocation.getShelfNumber()).isEqualTo("A-01");
        assertThat(savedLocation.getPositionNumber()).isEqualTo("001");
        assertThat(savedLocation.getEnabled()).isTrue();
        assertThat(savedLocation.getRemark()).isEqualTo("Test location");
    }

    @Test
    @DisplayName("创建库位 - 仓库不存在")
    void createLocation_WarehouseNotFound() {
        // Given
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> locationService.createLocation(999L, Zone.ZONE_A, "A-01", "001", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);

        // Verify
        verify(locationRepository, never()).save(any(Location.class));
    }

    @Test
    @DisplayName("创建库位 - 库位已存在")
    void createLocation_LocationAlreadyExists() {
        // Given
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));
        when(locationRepository.findByWarehouseCodeAndZoneAndShelfNumberAndPositionNumber(
                "WH01", Zone.ZONE_A, "A-01", "001"
        )).thenReturn(Optional.of(testLocation));

        // When & Then
        assertThatThrownBy(() -> locationService.createLocation(1L, Zone.ZONE_A, "A-01", "001", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.LOCATION_ALREADY_EXISTS);

        // Verify
        verify(locationRepository, never()).save(any(Location.class));
    }

    @Test
    @DisplayName("根据ID查询库位 - 成功")
    void getLocationById_Success() {
        // Given
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));

        // When
        Location result = locationService.getLocationById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getZone()).isEqualTo(Zone.ZONE_A);
    }

    @Test
    @DisplayName("根据ID查询库位 - 不存在")
    void getLocationById_NotFound() {
        // Given
        when(locationRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> locationService.getLocationById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.LOCATION_NOT_FOUND);
    }

    @Test
    @DisplayName("根据仓库ID查询所有库位")
    void getLocationsByWarehouse() {
        // Given
        Location location2 = Location.builder()
                .id(2L)
                .warehouse(testWarehouse)
                .zone(Zone.ZONE_B)
                .shelfNumber("B-01")
                .positionNumber("001")
                .enabled(true)
                .build();
        when(locationRepository.findByWarehouseId(1L)).thenReturn(Arrays.asList(testLocation, location2));

        // When
        List<Location> result = locationService.getLocationsByWarehouse(1L);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Location::getZone).containsExactly(Zone.ZONE_A, Zone.ZONE_B);
    }

    @Test
    @DisplayName("查询指定仓库的空闲库位")
    void getEmptyLocationsByWarehouse() {
        // Given
        when(locationRepository.findEmptyLocationsByWarehouseId(1L)).thenReturn(Arrays.asList(testLocation));

        // When
        List<Location> result = locationService.getEmptyLocationsByWarehouse(1L);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(testLocation);
    }

    @Test
    @DisplayName("更新库位信息 - 成功")
    void updateLocation_Success() {
        // Given
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));
        when(locationRepository.save(any(Location.class))).thenReturn(testLocation);

        // When
        Location result = locationService.updateLocation(1L, "Updated remark");

        // Then
        assertThat(result).isNotNull();
        verify(locationRepository).save(testLocation);
        assertThat(testLocation.getRemark()).isEqualTo("Updated remark");
    }

    @Test
    @DisplayName("更新库位信息 - 库位不存在")
    void updateLocation_NotFound() {
        // Given
        when(locationRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> locationService.updateLocation(999L, "New remark"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.LOCATION_NOT_FOUND);

        verify(locationRepository, never()).save(any(Location.class));
    }

    @Test
    @DisplayName("启用库位 - 成功")
    void enableLocation_Success() {
        // Given
        testLocation.setEnabled(false);
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));
        when(locationRepository.save(any(Location.class))).thenReturn(testLocation);

        // When
        Location result = locationService.enableLocation(1L);

        // Then
        assertThat(result.getEnabled()).isTrue();
        verify(locationRepository).save(testLocation);
    }

    @Test
    @DisplayName("禁用库位 - 成功")
    void disableLocation_Success() {
        // Given
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));
        when(locationRepository.save(any(Location.class))).thenReturn(testLocation);

        // When
        Location result = locationService.disableLocation(1L);

        // Then
        assertThat(result.getEnabled()).isFalse();
        verify(locationRepository).save(testLocation);
    }
}

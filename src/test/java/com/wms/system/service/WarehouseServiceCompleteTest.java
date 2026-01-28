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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WarehouseService 完整单元测试
 *
 * 测试范围：
 * 1. 创建仓库（成功/编码重复）
 * 2. 根据ID查询仓库（成功/不存在）
 * 3. 根据编码查询仓库（成功/不存在）
 * 4. 查询所有激活的仓库
 * 5. 查询所有仓库
 * 6. 更新仓库信息（全部字段/部分字段）
 * 7. 激活仓库
 * 8. 停用仓库
 * 9. 获取仓库的库位数量
 * 10. 边界情况测试
 *
 * @author WMS Team
 * @since 2026-01-28
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("仓库服务完整单元测试")
class WarehouseServiceCompleteTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private WarehouseService warehouseService;

    private Warehouse testWarehouse1;
    private Warehouse testWarehouse2;
    private Warehouse inactiveWarehouse;

    @BeforeEach
    void setUp() {
        testWarehouse1 = Warehouse.builder()
            .id(1L)
            .code("WH01")
            .name("主仓库")
            .address("上海市浦东新区张江高科技园区")
            .contact("张三 13800138000")
            .isActive(true)
            .build();

        testWarehouse2 = Warehouse.builder()
            .id(2L)
            .code("WH02")
            .name("分仓库")
            .address("北京市朝阳区")
            .contact("李四 13900139000")
            .isActive(true)
            .build();

        inactiveWarehouse = Warehouse.builder()
            .id(3L)
            .code("WH03")
            .name("已停用仓库")
            .address("深圳市南山区")
            .contact("王五 13700137000")
            .isActive(false)
            .build();
    }

    // ========== 创建仓库测试 ==========

    @Test
    @DisplayName("创建仓库 - 成功")
    void createWarehouse_Success() {
        // Given
        when(warehouseRepository.existsByCode("WH04")).thenReturn(false);
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse1);

        // When
        Warehouse result = warehouseService.createWarehouse(
            "WH04", "新仓库", "广州市天河区", "赵六 13600136000"
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);

        // Verify save was called with correct parameters
        ArgumentCaptor<Warehouse> captor = ArgumentCaptor.forClass(Warehouse.class);
        verify(warehouseRepository).save(captor.capture());
        Warehouse savedWarehouse = captor.getValue();
        assertThat(savedWarehouse.getCode()).isEqualTo("WH04");
        assertThat(savedWarehouse.getName()).isEqualTo("新仓库");
        assertThat(savedWarehouse.getAddress()).isEqualTo("广州市天河区");
        assertThat(savedWarehouse.getContact()).isEqualTo("赵六 13600136000");
        assertThat(savedWarehouse.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("创建仓库 - 编码已存在")
    void createWarehouse_CodeAlreadyExists() {
        // Given
        when(warehouseRepository.existsByCode("WH01")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> warehouseService.createWarehouse(
            "WH01", "重复仓库", null, null
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_ALREADY_EXISTS);

        // Verify save was never called
        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    @DisplayName("创建仓库 - 可选字段为空")
    void createWarehouse_OptionalFieldsNull() {
        // Given
        when(warehouseRepository.existsByCode("WH05")).thenReturn(false);
        Warehouse savedWarehouse = Warehouse.builder()
            .id(5L)
            .code("WH05")
            .name("简单仓库")
            .address(null)
            .contact(null)
            .isActive(true)
            .build();
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(savedWarehouse);

        // When
        Warehouse result = warehouseService.createWarehouse("WH05", "简单仓库", null, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAddress()).isNull();
        assertThat(result.getContact()).isNull();
    }

    // ========== 查询仓库测试 ==========

    @Test
    @DisplayName("根据ID查询仓库 - 成功")
    void getWarehouseById_Success() {
        // Given
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse1));

        // When
        Warehouse result = warehouseService.getWarehouseById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCode()).isEqualTo("WH01");
        assertThat(result.getName()).isEqualTo("主仓库");
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
        when(warehouseRepository.findByCode("WH01")).thenReturn(Optional.of(testWarehouse1));

        // When
        Warehouse result = warehouseService.getWarehouseByCode("WH01");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo("WH01");
        assertThat(result.getName()).isEqualTo("主仓库");
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
    void getAllActiveWarehouses_Success() {
        // Given
        List<Warehouse> activeWarehouses = Arrays.asList(testWarehouse1, testWarehouse2);
        when(warehouseRepository.findAllActive()).thenReturn(activeWarehouses);

        // When
        List<Warehouse> result = warehouseService.getAllActiveWarehouses();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(testWarehouse1, testWarehouse2);
        assertThat(result).allMatch(Warehouse::getIsActive);
    }

    @Test
    @DisplayName("查询所有激活的仓库 - 空列表")
    void getAllActiveWarehouses_EmptyList() {
        // Given
        when(warehouseRepository.findAllActive()).thenReturn(Collections.emptyList());

        // When
        List<Warehouse> result = warehouseService.getAllActiveWarehouses();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("查询所有仓库（包括已停用）")
    void getAllWarehouses_Success() {
        // Given
        List<Warehouse> allWarehouses = Arrays.asList(testWarehouse1, testWarehouse2, inactiveWarehouse);
        when(warehouseRepository.findAll()).thenReturn(allWarehouses);

        // When
        List<Warehouse> result = warehouseService.getAllWarehouses();

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).contains(testWarehouse1, testWarehouse2, inactiveWarehouse);
    }

    // ========== 更新仓库测试 ==========

    @Test
    @DisplayName("更新仓库 - 更新所有字段")
    void updateWarehouse_AllFields() {
        // Given
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse1));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse1);

        // When
        Warehouse result = warehouseService.updateWarehouse(
            1L, "更新后的名称", "更新后的地址", "更新后的联系方式"
        );

        // Then
        assertThat(result).isNotNull();
        verify(warehouseRepository).save(testWarehouse1);
        assertThat(testWarehouse1.getName()).isEqualTo("更新后的名称");
        assertThat(testWarehouse1.getAddress()).isEqualTo("更新后的地址");
        assertThat(testWarehouse1.getContact()).isEqualTo("更新后的联系方式");
    }

    @Test
    @DisplayName("更新仓库 - 只更新名称")
    void updateWarehouse_OnlyName() {
        // Given
        String originalAddress = testWarehouse1.getAddress();
        String originalContact = testWarehouse1.getContact();
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse1));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse1);

        // When
        Warehouse result = warehouseService.updateWarehouse(1L, "新名称", null, null);

        // Then
        assertThat(result.getName()).isEqualTo("新名称");
        assertThat(result.getAddress()).isEqualTo(originalAddress);  // 未改变
        assertThat(result.getContact()).isEqualTo(originalContact);  // 未改变
    }

    @Test
    @DisplayName("更新仓库 - 仓库不存在")
    void updateWarehouse_NotFound() {
        // Given
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> warehouseService.updateWarehouse(
            999L, "新名称", null, null
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);

        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    // ========== 激活/停用仓库测试 ==========

    @Test
    @DisplayName("激活仓库 - 成功")
    void activateWarehouse_Success() {
        // Given
        inactiveWarehouse.setIsActive(false);
        when(warehouseRepository.findById(3L)).thenReturn(Optional.of(inactiveWarehouse));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(inactiveWarehouse);

        // When
        Warehouse result = warehouseService.activateWarehouse(3L);

        // Then
        assertThat(result.getIsActive()).isTrue();
        verify(warehouseRepository).save(inactiveWarehouse);
    }

    @Test
    @DisplayName("激活仓库 - 仓库不存在")
    void activateWarehouse_NotFound() {
        // Given
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> warehouseService.activateWarehouse(999L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);
    }

    @Test
    @DisplayName("停用仓库 - 成功")
    void deactivateWarehouse_Success() {
        // Given
        testWarehouse1.setIsActive(true);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse1));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse1);

        // When
        Warehouse result = warehouseService.deactivateWarehouse(1L);

        // Then
        assertThat(result.getIsActive()).isFalse();
        verify(warehouseRepository).save(testWarehouse1);
    }

    @Test
    @DisplayName("停用仓库 - 仓库不存在")
    void deactivateWarehouse_NotFound() {
        // Given
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> warehouseService.deactivateWarehouse(999L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);
    }

    // ========== 库位数量统计测试 ==========

    @Test
    @DisplayName("获取仓库的库位数量 - 有库位")
    void getLocationCount_WithLocations() {
        // Given
        when(warehouseRepository.countLocationsByWarehouseId(1L)).thenReturn(50L);

        // When
        long count = warehouseService.getLocationCount(1L);

        // Then
        assertThat(count).isEqualTo(50L);
    }

    @Test
    @DisplayName("获取仓库的库位数量 - 无库位")
    void getLocationCount_NoLocations() {
        // Given
        when(warehouseRepository.countLocationsByWarehouseId(2L)).thenReturn(0L);

        // When
        long count = warehouseService.getLocationCount(2L);

        // Then
        assertThat(count).isZero();
    }

    // ========== 边界情况测试 ==========

    @Test
    @DisplayName("创建仓库 - 编码为空字符串")
    void createWarehouse_EmptyCode() {
        // Given
        when(warehouseRepository.existsByCode("")).thenReturn(false);

        // When & Then
        // 注意：实际应该在 Controller 层的验证中拦截，这里测试 Service 层行为
        assertThatThrownBy(() -> warehouseService.createWarehouse(
            "", "仓库", "地址", "联系方式"
        )).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("更新仓库 - 所有字段都为null")
    void updateWarehouse_AllFieldsNull() {
        // Given
        String originalName = testWarehouse1.getName();
        String originalAddress = testWarehouse1.getAddress();
        String originalContact = testWarehouse1.getContact();
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse1));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse1);

        // When
        Warehouse result = warehouseService.updateWarehouse(1L, null, null, null);

        // Then - 所有字段保持不变
        assertThat(result.getName()).isEqualTo(originalName);
        assertThat(result.getAddress()).isEqualTo(originalAddress);
        assertThat(result.getContact()).isEqualTo(originalContact);
    }

    @Test
    @DisplayName("重复激活已激活的仓库")
    void activateWarehouse_AlreadyActive() {
        // Given
        testWarehouse1.setIsActive(true);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse1));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse1);

        // When
        Warehouse result = warehouseService.activateWarehouse(1L);

        // Then - 仍然是激活状态
        assertThat(result.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("重复停用已停用的仓库")
    void deactivateWarehouse_AlreadyInactive() {
        // Given
        inactiveWarehouse.setIsActive(false);
        when(warehouseRepository.findById(3L)).thenReturn(Optional.of(inactiveWarehouse));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(inactiveWarehouse);

        // When
        Warehouse result = warehouseService.deactivateWarehouse(3L);

        // Then - 仍然是停用状态
        assertThat(result.getIsActive()).isFalse();
    }
}

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
 * WarehouseService 闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡?
 *
 * 濠电偞娼欓鍫ユ儊椤栨稓顩烽柟鎯х－濮樸劎绱掗悪鍛？闁诡喖锕闈涱吋閸涱収娼抽梺姹囧妼鐎氼參鎮х€圭姷鐤€闁告劑鍔岄～鐘绘煠?
 *
 * 濠电偞娼欓鍫ユ儊椤栫偛鎹堕柣鎴炆戦悵顖炴煥?
 * 1. 闂佸憡甯楃粙鎴犵磽閹惧顩烽柟鎯х－濮樸劑鏌ㄥ☉妯煎闁搞劍宀稿畷?缂傚倸鍊归悧婊堟偉濠婂牊鐓傜€广儱鎷嬪Σ濠氭煥?
 * 2. 闂佸搫绉烽～澶婄暤娑擃搳闂佸搫琚崕鎾敋濡や胶顩烽柟鎯х－濮樸劑鏌ㄥ☉妯煎闁搞劍宀稿畷?婵炴垶鎸哥粔鎾偤閵娾晛鎹舵い顓熷笧缁€?
 * 3. 闂佸搫绉烽～澶婄暤娴ｈ櫣纾介柡宥庡亞閸ㄦ娊鏌＄仦璇插姤妞ゆ洘顨嗙粋鎺楀箻瀹曞洦鐨戦梺鎸庣☉閻楀﹪宕瑰璺虹?婵炴垶鎸哥粔鎾偤閵娾晛鎹舵い顓熷笧缁€?
 * 4. 闂佸搫琚崕鎾敋濡ゅ懎绠ラ柍褜鍓熷鍨緞鐎ｎ剝绀嬪┑鐐跺蔼瀹曢潧鈻撻幋鐐殿浄闁规儳纾?
 * 5. 闂佸搫琚崕鎾敋濡ゅ懎绠ラ柍褜鍓熷鍨緞婢跺本灏濋柟?
 * 6. 闂佸搫娲ら悺銊╁蓟婵犲啰顩烽柟鎯х－濮樸劌菐閸ワ絽澧插ù?
 * 7. 濠电姷顣介崑鎾寸箾閼奸鍞虹紒顔哄妽閹?
 * 8. 闂佺顑嗙划搴ㄥ极閵堝棛顩烽柟鎯х－濮?
 * 9. 闂佸吋鍎抽崲鑼躲亹閸ャ劎顩烽柟鎯х－濮樸劑鏌ｉ妸銉ヮ仼缂併劏椴搁幏鍛吋閸℃ɑ顔嶉梻?
 *
 * @author WMS Team
 * @since 2025-01-23 (Phase 3.4)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
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
    @DisplayName("case-2")
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
    @DisplayName("case-3")
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
    @DisplayName("case-4")
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
    @DisplayName("case-5")
    void getWarehouseById_NotFound() {
        // Given
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> warehouseService.getWarehouseById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);
    }

    @Test
    @DisplayName("case-6")
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
    @DisplayName("case-7")
    void getWarehouseByCode_NotFound() {
        // Given
        when(warehouseRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> warehouseService.getWarehouseByCode("INVALID"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);
    }

    @Test
    @DisplayName("case-8")
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
    @DisplayName("case-9")
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
    @DisplayName("case-10")
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
    @DisplayName("case-11")
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
    @DisplayName("case-12")
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
    @DisplayName("case-13")
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
    @DisplayName("case-14")
    void getLocationCount() {
        // Given
        when(warehouseRepository.countLocationsByWarehouseId(1L)).thenReturn(10L);

        // When
        long count = warehouseService.getLocationCount(1L);

        // Then
        assertThat(count).isEqualTo(10L);
    }
}

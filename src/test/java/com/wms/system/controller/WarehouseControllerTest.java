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
 * WarehouseController 闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡?
 *
 * 婵炶揪缍€濞夋洟寮?Mockito 濠碘槅鍨崜婵堚偓姘懇楠炲秹鍩€椤掑嫬瀚夊璺侯槺鐠愨晠鎮硅閻楊厾妲愬┑鍥┾枖闁规儳鐡ㄩ弳鍫澝瑰鍐劉缂佷礁顕幏鐘诲即閻旇渹绮梺鍛婂笩濞夋稑鈻嶉幒妤佺劵闁哄嫬绻掔敮?
 *
 * 濠电偞娼欓鍫ユ儊椤栨粍鍟洪柛鈩冪懄绾句即鏌? * 1. 闂佸吋鍎抽崲鑼躲亹閸ヮ剙绠ラ柍褜鍓熷鍨緞婢跺本灏濋柟? * 2. 闂佸吋鍎抽崲鑼躲亹閸ヮ剙绠ラ柍褜鍓熷鍨緞鐎ｎ剝绀嬪┑鐐跺蔼瀹曢潧鈻撻幋鐐殿浄闁规儳纾?
 * 3. 闂佸搫绉烽～澶婄暤娑擃搳闂佸吋鍎抽崲鑼躲亹閸ャ劎顩烽柟鎯х－濮樸劑鏌ㄥ☉妯煎闁搞劍宀稿畷?婵炴垶鎸哥粔鎾偤閵娾晛鎹舵い顓熷笧缁€?
 * 4. 闂佸搫绉烽～澶婄暤娴ｈ櫣纾介柡宥庡亞閸ㄦ娊鏌ら幆褍妲荤憸鏉挎处缁傛帡骞樺畷鍥ㄧ殤闂佹寧绋戦悧濠囧垂濮樿泛绀?婵炴垶鎸哥粔鎾偤閵娾晛鎹舵い顓熷笧缁€?
 * 5. 闂佸憡甯楃粙鎴犵磽閹惧顩烽柟鎯х－濮樸劑鏌ㄥ☉妯煎闁搞劍宀稿畷?缂傚倸鍊归悧婊堟偉濠婂牊鐓傜€广儱鎷嬪Σ濠氭煥? * 6. 闂佸搫娲ら悺銊╁蓟婵犲啰顩烽柟鎯х－濮樸劑鏌ㄥ☉妯煎闁搞劍宀稿畷?婵炴垶鎸哥粔鎾偤閵娾晛鎹舵い顓熷笧缁€?
 * 7. 濠电姷顣介崑鎾寸箾閼奸鍞虹紒顔哄妽閹? * 8. 闂佺顑嗙划搴ㄥ极閵堝棛顩烽柟鎯х－濮?
 * 9. 闂佸吋鍎抽崲鑼躲亹閸ャ劎顩烽柟鎯х－濮樸劑鏌ｉ妸銉ヮ仼缂併劏椴搁幏鍛吋閸℃ɑ顔嶉梻? *
 * @author WMS Team
 * @since 2025-01-23 (Phase 3.4)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
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
    @DisplayName("case-2")
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
        assertThat(response.getBody())
                .extracting(WarehouseController.WarehouseResponse::code)
                .containsExactly("WH01", "WH02");
    }

    @Test
    @DisplayName("case-3")
    void getAllActiveWarehouses_Success() {
        // Given
        when(warehouseService.getAllActiveWarehouses()).thenReturn(Arrays.asList(testWarehouse));

        // When
        var response = warehouseController.getAllActiveWarehouses();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).isActive()).isTrue();
    }

    @Test
    @DisplayName("case-4")
    void getWarehouseById_Success() {
        // Given
        when(warehouseService.getWarehouseById(1L)).thenReturn(testWarehouse);

        // When
        var response = warehouseController.getWarehouseById(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(1L);
        assertThat(response.getBody().code()).isEqualTo("WH01");
    }

    @Test
    @DisplayName("case-5")
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
    @DisplayName("case-6")
    void getWarehouseByCode_Success() {
        // Given
        when(warehouseService.getWarehouseByCode("WH01")).thenReturn(testWarehouse);

        // When
        var response = warehouseController.getWarehouseByCode("WH01");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("WH01");
    }

    @Test
    @DisplayName("case-7")
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
    @DisplayName("case-8")
    void createWarehouse_Success() {
        // Given
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "WH02",
                "New Warehouse",
                "456 New St",
                "Jane Doe",
                "13900139000"
        );
        when(warehouseService.createWarehouse(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(testWarehouse);

        // When
        var response = warehouseController.createWarehouse(request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isNotNull();
        verify(warehouseService).createWarehouse("WH02", "New Warehouse", "456 New St", "Jane Doe", "13900139000");
    }

    @Test
    @DisplayName("case-9")
    void createWarehouse_CodeAlreadyExists() {
        // Given
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "WH01",
                "Duplicate Warehouse",
                null,
                null,
                null
        );
        when(warehouseService.createWarehouse(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorKeys.WAREHOUSE_ALREADY_EXISTS));

        // When & Then
        assertThatThrownBy(() -> warehouseController.createWarehouse(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("case-10")
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
    @DisplayName("case-11")
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
    @DisplayName("case-12")
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
    @DisplayName("case-13")
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
    @DisplayName("case-14")
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

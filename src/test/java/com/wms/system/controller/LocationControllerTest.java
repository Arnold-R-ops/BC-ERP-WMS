package com.wms.system.controller;

import com.wms.system.controller.LocationController.CreateLocationRequest;
import com.wms.system.controller.LocationController.UpdateLocationRequest;
import com.wms.system.dto.LocationResponse;
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
 * LocationController 闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡?
 *
 * 婵炶揪缍€濞夋洟寮?Mockito 濠碘槅鍨崜婵堚偓姘懇楠炲秹鍩€椤掑嫬瀚夊璺侯槺鐠愨晠鎮硅閻楊厾妲愬┑鍥┾枖闁规儳鐡ㄩ弳鍫澝瑰鍐劉缂佷礁顕幏鐘诲即閻旇渹绮梺鍛婂笩濞夋稑鈻嶉幒妤佺劵闁哄嫬绻掔敮?
 *
 * 濠电偞娼欓鍫ユ儊椤栨粍鍟洪柛鈩冪懄绾句即鏌? * 1. 闂佸搫绉烽～澶婄暤娑擃搳闂佸吋鍎抽崲鑼躲亹閸ャ劍鍎熼柟鎹愬皺缁夋挳鏌ㄥ☉妯煎闁搞劍宀稿畷?婵炴垶鎸哥粔鎾偤閵娾晛鎹舵い顓熷笧缁€?
 * 2. 闂佸搫绉烽～澶婄暤娴ｅ湱顩烽柟鎯х－濮樷問D闂佸吋鍎抽崲鑼躲亹閸ヮ剙绠ラ柍褜鍓熷鍨緞婵犲嫭鐨戞繛? * 3. 闂佸吋鍎抽崲鑼躲亹閸ヮ剙绠伴柛銉戝懏姣庢繛瀵稿У閹稿摜鑺遍柆宥嗗剭闁告洦鍘搁弫鍕⒒閸屾稑绲荤紒銊ㄩ哺閹? * 4. 闂佸憡甯楃粙鎴犵磽閹惧瓨鍎熼柟鎹愬皺缁夋挳鏌ㄥ☉妯煎闁搞劍宀稿畷?婵炲濮甸幐鍝ヨ姳鏉堛劎鈻旂€广儱鎳愰幗鐘绘煕?闁圭厧鐡ㄩ幐椋庣礊閸涱収鍟呴柟缁樺笧閹界娀鏌涢敂鑽ゅ帨缂?
 * 5. 闂佸搫娲ら悺銊╁蓟婵犲啯鍎熼柟鎹愬皺缁夋挳鏌ㄥ☉妯煎闁搞劍宀稿畷?婵炴垶鎸哥粔鎾偤閵娾晛鎹舵い顓熷笧缁€?
 * 6. 闂佸憡鍑归崹鎶藉极閵堝棙鍎熼柟鎹愬皺缁?
 * 7. 缂備礁鍊烽懗鍫曞极閵堝棙鍎熼柟鎹愬皺缁?
 *
 * @author WMS Team
 * @since 2025-01-23 (Phase 3.4)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
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
    @DisplayName("case-2")
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
    @DisplayName("case-3")
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
    @DisplayName("case-4")
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
        assertThat(response.getBody()).extracting(LocationResponse::getZone)
                .containsExactly(Zone.ZONE_A, Zone.ZONE_B);
    }

    @Test
    @DisplayName("case-5")
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
    @DisplayName("case-6")
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
    @DisplayName("case-7")
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
    @DisplayName("case-8")
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
    @DisplayName("case-9")
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
    @DisplayName("case-10")
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
    @DisplayName("case-11")
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
    @DisplayName("case-12")
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

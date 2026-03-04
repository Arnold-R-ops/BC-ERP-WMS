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
 * LocationService 闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡?
 *
 * 濠电偞娼欓鍫ユ儊椤栨稒鍎熼柟鎹愬皺缁夊绱掗悪鍛？闁诡喖锕闈涱吋閸涱収娼抽梺姹囧妼鐎氼參鎮х€圭姷鐤€闁告劑鍔岄～鐘绘煠?
 *
 * 濠电偞娼欓鍫ユ儊椤栫偛鎹堕柣鎴炆戦悵顖炴煥?
 * 1. 闂佸憡甯楃粙鎴犵磽閹惧瓨鍎熼柟鎹愬皺缁夋挳鏌ㄥ☉妯煎闁搞劍宀稿畷?婵炲濮甸幐鍝ヨ姳鏉堛劎鈻旂€广儱鎳愰幗鐘绘煕?闁圭厧鐡ㄩ幐椋庣礊閸涱収鍟呴柟缁樺笧閹界娀鏌涢敂鑽ゅ帨缂?
 * 2. 闂佸搫绉烽～澶婄暤娑擃搳闂佸搫琚崕鎾敋濡や焦鍎熼柟鎹愬皺缁夋挳鏌ㄥ☉妯煎闁搞劍宀稿畷?婵炴垶鎸哥粔鎾偤閵娾晛鎹舵い顓熷笧缁€?
 * 3. 闂佸搫绉烽～澶婄暤娴ｅ湱顩烽柟鎯х－濮樷問D闂佸搫琚崕鎾敋濡や焦鍎熼柟鎹愬皺缁?
 * 4. 闂佸搫琚崕鎾敋濡や胶顩烽柟鎯х－濮樸劑鏌ｉ妸銉ヮ伀闁宠鐗犲濠氬箵閹烘梹鐨戞繛?
 * 5. 闂佸搫娲ら悺銊╁蓟婵犲啯鍎熼柟鎹愬皺缁夋潙菐閸ワ絽澧插ù?
 * 6. 闂佸憡鍑归崹鎶藉极閵堝棙鍎熼柟鎹愬皺缁?
 * 7. 缂備礁鍊烽懗鍫曞极閵堝棙鍎熼柟鎹愬皺缁?
 *
 * @author WMS Team
 * @since 2025-01-23 (Phase 3.4)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
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
    @DisplayName("case-2")
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
    @DisplayName("case-3")
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
    @DisplayName("case-4")
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
    @DisplayName("case-5")
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
    @DisplayName("case-6")
    void getLocationById_NotFound() {
        // Given
        when(locationRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> locationService.getLocationById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.LOCATION_NOT_FOUND);
    }

    @Test
    @DisplayName("case-7")
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
    @DisplayName("case-8")
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
    @DisplayName("case-9")
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
    @DisplayName("case-10")
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
    @DisplayName("case-11")
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
    @DisplayName("case-12")
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

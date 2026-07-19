package com.wms.system.service;

import com.wms.system.dto.inventory.InventoryDetailDto;
import com.wms.system.dto.inventory.InventorySummaryDto;
import com.wms.system.dto.inventory.LocationViewDto;
import com.wms.system.entity.Location;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.StockStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryQueryServiceTest {

    @Mock
    private InventoryBatchRepository inventoryBatchRepository;

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private InventoryQueryService inventoryQueryService;

    @Test
    void getSummaryMapsProjectionAndUsesAvailableStockForStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        InventoryBatchRepository.InventorySummaryRow row = summaryRow(32, 5, 27, 30);
        when(inventoryBatchRepository.findInventorySummaryRows("", pageable))
            .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(pageable, null);

        assertThat(result.getTotalElements()).isEqualTo(1);
        InventorySummaryDto summary = result.getContent().get(0);
        assertThat(summary.getProductSkuId()).isEqualTo(1L);
        assertThat(summary.getSkuInfo().getSkuCode()).isEqualTo("SKU-001");
        assertThat(summary.getWarehouseNames()).containsExactly("Warehouse A", "Warehouse B");
        assertThat(summary.getTotalQuantity()).isEqualTo(32);
        assertThat(summary.getReservedQuantity()).isEqualTo(5);
        assertThat(summary.getAvailableQuantity()).isEqualTo(27);
        assertThat(summary.getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);
    }

    @Test
    void getSummaryMarksSufficientStock() {
        Pageable pageable = PageRequest.of(0, 20);
        InventoryBatchRepository.InventorySummaryRow row = summaryRow(32, 5, 27, 20);
        when(inventoryBatchRepository.findInventorySummaryRows("tea", pageable))
            .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        InventorySummaryDto summary = inventoryQueryService
            .getSummary(pageable, "  tea  ")
            .getContent()
            .get(0);

        assertThat(summary.getStockStatus()).isEqualTo(StockStatus.SUFFICIENT);
        verify(inventoryBatchRepository).findInventorySummaryRows("tea", pageable);
    }

    @Test
    void getSummaryReturnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 20);
        when(inventoryBatchRepository.findInventorySummaryRows("", pageable))
            .thenReturn(Page.empty(pageable));

        assertThat(inventoryQueryService.getSummary(pageable, "")).isEmpty();
    }

    @Test
    void getDetailsBySkuIdMapsDatabaseRows() {
        InventoryBatchRepository.InventoryDetailRow row = detailRow("BATCH-001", LocalDate.of(2027, 1, 1));
        when(inventoryBatchRepository.findActiveDetailRowsByProductSkuId(1L)).thenReturn(List.of(row));

        List<InventoryDetailDto> result = inventoryQueryService.getDetailsBySkuId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBatchCode()).isEqualTo("BATCH-001");
        assertThat(result.get(0).getTraceCode()).isEqualTo("BATCH-001");
        assertThat(result.get(0).getQuantity()).isEqualTo(24);
        assertThat(result.get(0).getReservedQuantity()).isEqualTo(4);
        assertThat(result.get(0).getAvailableQuantity()).isEqualTo(20);
        assertThat(result.get(0).getPackageStatus()).isNotBlank();
    }

    @Test
    void getDetailsBySkuIdReturnsEmptyList() {
        when(inventoryBatchRepository.findActiveDetailRowsByProductSkuId(99L)).thenReturn(List.of());

        assertThat(inventoryQueryService.getDetailsBySkuId(99L)).isEmpty();
    }

    @Test
    void getLocationViewMapsLocationAndBatches() {
        Warehouse warehouse = Warehouse.builder().id(1L).name("Main Warehouse").build();
        Location location = Location.builder()
            .id(2L)
            .locationCode("WH01-A-01")
            .warehouse(warehouse)
            .build();
        InventoryBatchRepository.InventoryDetailRow row = detailRow("BATCH-002", null);
        when(locationRepository.findByLocationCode("WH01-A-01")).thenReturn(Optional.of(location));
        when(inventoryBatchRepository.findActiveDetailRowsByLocationCode("WH01-A-01"))
            .thenReturn(List.of(row));

        LocationViewDto result = inventoryQueryService.getLocationView("WH01-A-01");

        assertThat(result.getWarehouseName()).isEqualTo("Main Warehouse");
        assertThat(result.getBatches()).extracting(InventoryDetailDto::getBatchCode)
            .containsExactly("BATCH-002");
        assertThat(result.getBatches().get(0).getExpiryDate()).isNull();
    }

    @Test
    void getLocationViewReturnsEmptyBatchesWhenLocationIsEmpty() {
        Location location = Location.builder().id(2L).locationCode("WH01-A-01").build();
        when(locationRepository.findByLocationCode("WH01-A-01")).thenReturn(Optional.of(location));
        when(inventoryBatchRepository.findActiveDetailRowsByLocationCode("WH01-A-01"))
            .thenReturn(List.of());

        assertThat(inventoryQueryService.getLocationView("WH01-A-01").getBatches()).isEmpty();
    }

    @Test
    void getLocationViewRejectsUnknownLocation() {
        when(locationRepository.findByLocationCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryQueryService.getLocationView("UNKNOWN"))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo(ErrorKeys.LOCATION_NOT_FOUND);
                assertThat(exception.getParams()).containsEntry("locationCode", "UNKNOWN");
            });
    }

    private InventoryBatchRepository.InventorySummaryRow summaryRow(
        Number total,
        Number reserved,
        Number available,
        Number safetyStock
    ) {
        InventoryBatchRepository.InventorySummaryRow row = mock(InventoryBatchRepository.InventorySummaryRow.class);
        when(row.getProductSkuId()).thenReturn(1L);
        when(row.getProductName()).thenReturn("Tea 500g");
        when(row.getSkuCode()).thenReturn("SKU-001");
        when(row.getSpecs()).thenReturn("500g x 12");
        when(row.getPackUnit()).thenReturn("box");
        when(row.getConversionRate()).thenReturn(12);
        when(row.getSafetyStock()).thenReturn(safetyStock);
        when(row.getTotalQuantity()).thenReturn(total);
        when(row.getTotalReservedQuantity()).thenReturn(reserved);
        when(row.getTotalAvailableQuantity()).thenReturn(available);
        when(row.getFurthestExpiryDate()).thenReturn(LocalDate.of(2027, 1, 1));
        when(row.getWarehouseNames()).thenReturn("Warehouse A,Warehouse B,Warehouse A");
        return row;
    }

    private InventoryBatchRepository.InventoryDetailRow detailRow(String batchCode, LocalDate expiryDate) {
        InventoryBatchRepository.InventoryDetailRow row = mock(InventoryBatchRepository.InventoryDetailRow.class);
        when(row.getBatchCode()).thenReturn(batchCode);
        when(row.getWarehouseName()).thenReturn("Main Warehouse");
        when(row.getLocationCode()).thenReturn("WH01-A-01");
        when(row.getQuantity()).thenReturn(24);
        when(row.getReservedQuantity()).thenReturn(4);
        when(row.getAvailableQuantity()).thenReturn(20);
        when(row.getExpiryDate()).thenReturn(expiryDate);
        when(row.getConversionRate()).thenReturn(12);
        return row;
    }
}

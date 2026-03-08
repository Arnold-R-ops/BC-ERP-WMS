package com.wms.system.service;

import com.wms.system.dto.sales.BatchOptionDto;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Product;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SalesEntryService Tests")
class SalesEntryServiceTest {

    @Mock private InventoryBatchRepository inventoryBatchRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks
    private SalesEntryService salesEntryService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .id(1L)
                .name("Test Product")
                .barcode("BAR001")
                .unitPrice(new BigDecimal("10.00"))
                .nearExpiryDays(30)
                .perPackQty(12)
                .build();
    }

    // ========== downloadExcelTemplate ==========

    @Test
    @DisplayName("downloadExcelTemplate - returns non-empty byte array")
    void testDownloadExcelTemplate() {
        byte[] result = salesEntryService.downloadExcelTemplate();

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    // ========== importSalesOrderFromExcel ==========

    @Test
    @DisplayName("importSalesOrderFromExcel - throws when file is null/empty")
    void testImportSalesOrderFromExcel_EmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]
        );

        assertThatThrownBy(() -> salesEntryService.importSalesOrderFromExcel(emptyFile))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("importSalesOrderFromExcel - throws when file is not xlsx")
    void testImportSalesOrderFromExcel_WrongFormat() {
        MockMultipartFile csvFile = new MockMultipartFile(
                "file", "orders.csv", "text/csv", "col1,col2\n1,2".getBytes()
        );

        assertThatThrownBy(() -> salesEntryService.importSalesOrderFromExcel(csvFile))
                .isInstanceOf(BusinessException.class);
    }

    // ========== getBatchOptions ==========

    @Test
    @DisplayName("getBatchOptions - returns all batches sorted by FEFO when rejectNearExpiry false")
    void testGetBatchOptions_AllBatches() {
        InventoryBatch freshBatch = buildBatch(1L, "BC001", 100, LocalDate.now().plusDays(60));
        InventoryBatch nearExpiryBatch = buildBatch(2L, "BC002", 50, LocalDate.now().plusDays(10));

        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
                .thenReturn(List.of(nearExpiryBatch, freshBatch)); // FEFO: near-expiry first

        List<BatchOptionDto> result = salesEntryService.getBatchOptions(1L, 50, false);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getBatchOptions - filters out near-expiry batches when rejectNearExpiry true")
    void testGetBatchOptions_RejectNearExpiry() {
        // nearExpiryDays = 30; batch expires in 10 days → should be filtered out
        InventoryBatch freshBatch = buildBatch(1L, "BC001", 100, LocalDate.now().plusDays(60));
        InventoryBatch nearExpiryBatch = buildBatch(2L, "BC002", 50, LocalDate.now().plusDays(10));

        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
                .thenReturn(List.of(nearExpiryBatch, freshBatch));

        List<BatchOptionDto> result = salesEntryService.getBatchOptions(1L, 50, true);

        // Only fresh batch should remain (10 days ≤ 30 nearExpiryDays → filtered)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBatchCode()).isEqualTo("BC001");
    }

    @Test
    @DisplayName("getBatchOptions - marks fresh/warning freshness status correctly")
    void testGetBatchOptions_FreshnessStatus() {
        // nearExpiryDays = 30
        InventoryBatch freshBatch = buildBatch(1L, "BC001", 100, LocalDate.now().plusDays(60));
        InventoryBatch warningBatch = buildBatch(2L, "BC002", 50, LocalDate.now().plusDays(20));

        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
                .thenReturn(List.of(warningBatch, freshBatch));

        List<BatchOptionDto> result = salesEntryService.getBatchOptions(1L, 50, false);

        assertThat(result).hasSize(2);
        // warning first (FEFO), fresh second
        assertThat(result.get(0).getFreshnessStatus()).isEqualTo("WARNING");
        assertThat(result.get(1).getFreshnessStatus()).isEqualTo("FRESH");
    }

    @Test
    @DisplayName("getBatchOptions - throws when product not found")
    void testGetBatchOptions_ProductNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salesEntryService.getBatchOptions(99L, 50, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("getBatchOptions - returns empty when no active batches")
    void testGetBatchOptions_NoBatches() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
                .thenReturn(List.of());

        List<BatchOptionDto> result = salesEntryService.getBatchOptions(1L, 50, false);

        assertThat(result).isEmpty();
    }

    // ========== Helper ==========

    private InventoryBatch buildBatch(Long id, String batchCode, int qty, LocalDate expiryDate) {
        return InventoryBatch.builder()
                .id(id)
                .batchCode(batchCode)
                .quantity(qty)
                .expiryDate(expiryDate)
                .active(true)
                .locationCode("WH01-ZONE_A-A-01-001")
                .build();
    }
}

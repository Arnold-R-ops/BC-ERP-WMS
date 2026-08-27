package com.wms.system.service;

import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService Tests")
class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryBatchRepository inventoryBatchRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    @DisplayName("getTotalStock - returns value from repository")
    void testGetTotalStock_HasValue() {
        when(inventoryBatchRepository.sumQuantityByProductSku(1L)).thenReturn(350);

        Integer result = inventoryService.getTotalStock(1L);

        assertThat(result).isEqualTo(350);
    }

    @Test
    @DisplayName("getTotalStock - returns 0 when repository returns null")
    void testGetTotalStock_ReturnsZeroWhenNull() {
        when(inventoryBatchRepository.sumQuantityByProductSku(1L)).thenReturn(null);

        Integer result = inventoryService.getTotalStock(1L);

        assertThat(result).isEqualTo(0);
    }
}

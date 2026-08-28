package com.wms.system.service;

import com.wms.system.entity.ProductSku;
import com.wms.system.entity.Warehouse;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InboundReferenceServiceTest {

    @Test
    void exposesOnlySafeOperationalReferenceFields() {
        ProductSkuRepository skuRepository = mock(ProductSkuRepository.class);
        WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);
        InboundReferenceService service = new InboundReferenceService(
            skuRepository, warehouseRepository);
        ProductSku sku = ProductSku.builder()
            .id(11L).skuCode("SKU-11").name("Sample").barcode("690000000011")
            .enabled(true).expiryManagementEnabled(true).supplier("SECRET")
            .build();
        Warehouse warehouse = Warehouse.builder()
            .id(8L).code("WH8").name("Main").isActive(true).build();
        when(skuRepository.findAllOperationalOrderBySkuCodeAsc()).thenReturn(List.of(sku));
        when(warehouseRepository.findAllActive()).thenReturn(List.of(warehouse));

        assertThat(service.listOperationalSkus()).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(11L);
            assertThat(response.expiryManagementEnabled()).isTrue();
            assertThat(response.toString()).doesNotContain("SECRET");
        });
        assertThat(service.listActiveWarehouses()).containsExactly(
            new com.wms.system.dto.receiving.InboundReferenceWarehouseResponse(
                8L, "WH8", "Main"));
        verify(skuRepository).findAllOperationalOrderBySkuCodeAsc();
        verify(warehouseRepository).findAllActive();
    }
}

package com.wms.system.service;
import com.wms.system.entity.*;
import com.wms.system.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WarehouseStockThresholdServiceTest {
    final WarehouseStockThresholdRepository rules=mock(WarehouseStockThresholdRepository.class);
    final WarehouseRepository warehouses=mock(WarehouseRepository.class);
    final ProductSkuRepository products=mock(ProductSkuRepository.class);
    final WarehouseStockThresholdService service=new WarehouseStockThresholdService(rules,warehouses,products);
    final ProductSku sku=ProductSku.builder().id(1L).minStock(8).build();
    final Warehouse warehouse=Warehouse.builder().id(2L).isActive(true).build();

    @Test void oneWarehouseUsesExistingThresholdButMultipleWarehousesNeedTheirOwn(){
        when(warehouses.findById(2L)).thenReturn(Optional.of(warehouse));
        when(warehouses.findAllActive()).thenReturn(List.of(warehouse));
        assertThat(service.threshold(sku,2L)).isEqualTo(8);
        when(warehouses.findAllActive()).thenReturn(List.of(warehouse,Warehouse.builder().id(3L).isActive(true).build()));
        assertThat(service.threshold(sku,2L)).isNull();
    }
    @Test void explicitWarehouseMinimumWinsEvenWhenZero(){
        when(warehouses.findById(2L)).thenReturn(Optional.of(warehouse));
        var rule=new WarehouseStockThreshold();rule.setMinStock(0);
        when(rules.findByProductSkuIdAndWarehouseId(1L,2L)).thenReturn(Optional.of(rule));
        assertThat(service.threshold(sku,2L)).isZero();
        verify(warehouses,never()).findAllActive();
    }
    @Test void firstSaveLocksSkuAndRejectsInvalidWarehouseOrNegativeMinimum(){
        assertThatThrownBy(()->service.save(1L,2L,-1)).hasMessage("VALIDATION_FAILED");
        when(products.findByIdForThresholdUpdate(1L)).thenReturn(Optional.of(sku));
        assertThatThrownBy(()->service.save(1L,2L,10)).hasMessage("VALIDATION_FAILED");
        verify(rules,never()).save(any());
        when(warehouses.findById(2L)).thenReturn(Optional.of(warehouse));
        service.save(1L,2L,10);
        verify(rules).save(argThat(r->r.getProductSku()==sku && r.getWarehouse()==warehouse && r.getMinStock()==10));
    }
}

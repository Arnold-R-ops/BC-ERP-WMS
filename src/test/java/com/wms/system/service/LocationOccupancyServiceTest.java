package com.wms.system.service;

import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.ProductSku;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationOccupancyServiceTest {

    @Mock private InventoryBatchRepository inventoryBatchRepository;
    @Mock private LocationRepository locationRepository;

    private LocationOccupancyService service;

    @BeforeEach
    void setUp() {
        service = new LocationOccupancyService(inventoryBatchRepository, locationRepository);
    }

    @Test
    void canStoreAndStrictValidationBothAllowTheMatchingBatchIdentity() {
        ProductSku product = ProductSku.builder().id(21L).build();
        Location location = Location.builder().id(31L).locationCode("A-01").build();
        InventoryBatch existing = InventoryBatch.builder()
            .productSku(product)
            .location(location)
            .batchCode("B-01")
            .quantity(10)
            .active(true)
            .build();
        when(inventoryBatchRepository.findPositiveActiveBatchesByLocationId(31L))
            .thenReturn(List.of(existing));

        assertThat(service.canStore(location, product, "B-01")).isTrue();
        assertThatCode(() -> service.validateCanStore(location, product, "B-01"))
            .doesNotThrowAnyException();
    }

    @Test
    void canStoreAndStrictValidationBothRejectAnotherBatchIdentity() {
        ProductSku product = ProductSku.builder().id(21L).build();
        Location location = Location.builder().id(31L).locationCode("A-01").build();
        InventoryBatch existing = InventoryBatch.builder()
            .productSku(product)
            .location(location)
            .batchCode("OTHER-BATCH")
            .quantity(10)
            .active(true)
            .build();
        when(inventoryBatchRepository.findPositiveActiveBatchesByLocationId(31L))
            .thenReturn(List.of(existing));

        assertThat(service.canStore(location, product, "B-01")).isFalse();
        assertThatThrownBy(() -> service.validateCanStore(location, product, "B-01"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.LOCATION_BATCH_MIXING_FORBIDDEN));
    }
}

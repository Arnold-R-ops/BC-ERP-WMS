package com.wms.system.entity;

import com.wms.system.entity.enums.InventoryQualityStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryBatchQualityStatusTest {

    @Test
    void onlyAvailableActiveAndDateValidStockIsSaleable() {
        InventoryBatch batch = batch();

        assertThat(batch.isSaleable()).isTrue();
        assertThat(batch.getAvailableQuantity()).isEqualTo(8);

        batch.setQualityStatus(InventoryQualityStatus.QUARANTINED);

        assertThat(batch.isSaleable()).isFalse();
        assertThat(batch.getAvailableQuantity()).isZero();
        assertThatThrownBy(() -> batch.reserveQuantity(1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiredAvailableStatusStillFailsTheDateSafetyGate() {
        InventoryBatch batch = batch();
        batch.setReservedQuantity(0);
        batch.setExpiryDate(LocalDate.now().minusDays(1));

        assertThat(batch.getQualityStatus()).isEqualTo(InventoryQualityStatus.AVAILABLE);
        assertThat(batch.isSaleable()).isFalse();
        assertThat(batch.getAvailableQuantity()).isZero();
    }

    private InventoryBatch batch() {
        return InventoryBatch.builder()
            .batchCode("QUALITY-1")
            .quantity(10)
            .reservedQuantity(2)
            .initialQuantity(10)
            .active(true)
            .expiryDate(LocalDate.now().plusDays(30))
            .build();
    }
}

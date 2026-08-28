package com.wms.system.entity;

import com.wms.system.dto.InventoryBatchResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryBatchExpiryTest {

    @Test
    void newSkuDefaultsToNoExpiryManagement() {
        ProductSku productSku = ProductSku.builder().build();

        assertThat(productSku.getExpiryManagementEnabled()).isFalse();
    }

    @Test
    void undatedBatchIsNeitherExpiredNorExpiringSoon() {
        InventoryBatch batch = InventoryBatch.builder()
            .expiryDate(null)
            .build();

        assertThat(batch.isExpired()).isFalse();
        assertThat(batch.isExpiringSoon()).isFalse();
        assertThat(batch.getDaysUntilExpiry()).isNull();
    }

    @Test
    void datedBatchRetainsExpirySemantics() {
        InventoryBatch expired = InventoryBatch.builder()
            .expiryDate(LocalDate.now().minusDays(1))
            .build();
        InventoryBatch expiringSoon = InventoryBatch.builder()
            .expiryDate(LocalDate.now().plusDays(3))
            .build();

        assertThat(expired.isExpired()).isTrue();
        assertThat(expiringSoon.isExpiringSoon()).isTrue();
        assertThat(expiringSoon.getDaysUntilExpiry()).isEqualTo(3L);
    }

    @Test
    void undatedResponseDoesNotExposeAnArtificialMaximumDayCount() {
        InventoryBatchResponse response = InventoryBatchResponse.builder()
            .expiryDate(null)
            .build();

        assertThat(response.isExpired()).isFalse();
        assertThat(response.isExpiringSoon()).isFalse();
        assertThat(response.getDaysUntilExpiry()).isNull();
    }
}

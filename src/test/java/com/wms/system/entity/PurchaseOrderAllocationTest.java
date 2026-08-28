package com.wms.system.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderAllocationTest {

    @Test
    void closedRemainderAlreadyIncludedInShippedQuantityIsNotSubtractedTwice() {
        PurchaseOrderAllocation allocation = PurchaseOrderAllocation.builder()
            .plannedQuantity(100)
            .shippedQuantity(60)
            .receivedQuantity(50)
            .closedQuantity(10)
            .build();

        assertThat(allocation.availableToShipQuantity()).isEqualTo(40);
        assertThat(allocation.outstandingQuantity()).isEqualTo(40);
    }
}

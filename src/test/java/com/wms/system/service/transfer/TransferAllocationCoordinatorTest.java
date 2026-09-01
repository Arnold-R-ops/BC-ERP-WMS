package com.wms.system.service.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.repository.*;
import com.wms.system.service.DomainOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferAllocationCoordinatorTest {

    @Mock private TransferAllocationRepository allocationRepository;
    @Mock private TransferOrderLineRepository lineRepository;
    @Mock private InventoryBatchRepository batchRepository;
    @Mock private TransferEventRepository eventRepository;
    @Mock private DomainOutboxService outboxService;

    private TransferAllocationCoordinator coordinator;
    private TransferOrderLine line;
    private InventoryBatch source;
    private TransferAllocation allocation;

    @BeforeEach
    void setUp() {
        coordinator = new TransferAllocationCoordinator(allocationRepository, lineRepository,
            batchRepository, eventRepository, outboxService, new ObjectMapper());
        Warehouse warehouse = Warehouse.builder().id(10L).code("WH-A").name("Warehouse A")
            .isActive(true).build();
        ProductSku sku = ProductSku.builder().id(20L).skuCode("SKU-20").name("Widget").build();
        Location location = Location.builder().id(30L).warehouse(warehouse).warehouseCode("WH-A")
            .zone(Zone.ZONE_A).shelfNumber("A-01").positionNumber("001")
            .locationCode("WH-A-ZONE_A-A-01-001").enabled(true).build();
        TransferOrder order = TransferOrder.builder().id(40L).transferNumber("TR-40")
            .sourceWarehouse(warehouse).destinationWarehouse(
                Warehouse.builder().id(11L).code("WH-B").name("Warehouse B").isActive(true).build())
            .businessStatus(TransferBusinessStatus.APPROVED)
            .outboundStatus(TransferOutboundStatus.PENDING)
            .inboundStatus(TransferInboundStatus.NOT_IN_TRANSIT)
            .approvalModeSnapshot(TransferApprovalMode.DIRECT)
            .reason("rebalance").requestedBy(1L).requestedByName("planner").build();
        line = TransferOrderLine.builder().id(50L).productSku(sku)
            .requestedQuantity(5).allocatedQuantity(5).build();
        order.addLine(line);
        source = batch(60L, sku, location, 10, 5);
        allocation = TransferAllocation.builder().id(70L).transferLine(line).sourceBatch(source)
            .allocatedQuantity(5).status(TransferAllocationStatus.ACTIVE).build();
        lenient().when(allocationRepository.findBySourceBatchIdsForUpdate(anyCollection()))
            .thenReturn(List.of(allocation));
        lenient().when(batchRepository.findByIdForPosting(60L)).thenReturn(Optional.of(source));
    }

    @Test
    void previewProjectionCountsOnlyOpenAllocationsOnApprovedTransfers() {
        when(allocationRepository.findBySourceBatchIds(List.of(60L)))
            .thenReturn(List.of(allocation));

        assertThat(coordinator.openReservedQuantities(List.of(60L)))
            .containsExactlyEntriesOf(java.util.Map.of(60L, 5));
    }

    @Test
    void safetyEvacuationMovesOpenTransferReservationToAnotherSaleableBatch() {
        InventoryBatch target = batch(61L, line.getProductSku(), source.getLocation(), 8, 0);
        when(batchRepository.findTransferCandidatesForUpdate(10L, 20L)).thenReturn(List.of(target));

        int affected = coordinator.evacuate(List.of(60L), "QUALITY_HOLD", 99L, "qa-user");

        assertThat(affected).isEqualTo(5);
        assertThat(source.getReservedQuantity()).isZero();
        assertThat(target.getReservedQuantity()).isEqualTo(5);
        assertThat(line.getAllocatedQuantity()).isEqualTo(5);
        assertThat(allocation.getReleasedQuantity()).isEqualTo(5);
        assertThat(allocation.getStatus()).isEqualTo(TransferAllocationStatus.RELEASED);
        ArgumentCaptor<TransferAllocation> replacement = ArgumentCaptor.forClass(TransferAllocation.class);
        verify(allocationRepository, times(2)).save(replacement.capture());
        assertThat(replacement.getAllValues()).anySatisfy(item -> {
            assertThat(item.getSourceBatch()).isSameAs(target);
            assertThat(item.getAllocatedQuantity()).isEqualTo(5);
            assertThat(item.getStatus()).isEqualTo(TransferAllocationStatus.ACTIVE);
        });
        verify(eventRepository).save(argThat(event ->
            event.getEventType().equals("TRANSFER_ALLOCATION_EVACUATED_FOR_SAFETY")
                && event.getActorId().equals(99L)));
        verify(outboxService).append(eq("TRANSFER_ALLOCATION_CHANGED"), eq("TransferOrder"),
            eq(40L), anyMap());
    }

    @Test
    void safetyEvacuationLeavesTransferPartiallyUnallocatedWhenNoAlternativeExists() {
        when(batchRepository.findTransferCandidatesForUpdate(10L, 20L)).thenReturn(List.of());

        int affected = coordinator.evacuate(List.of(60L), "DAMAGE_REPORT", 99L, "qa-user");

        assertThat(affected).isEqualTo(5);
        assertThat(source.getReservedQuantity()).isZero();
        assertThat(line.getAllocatedQuantity()).isZero();
        assertThat(allocation.getReleasedQuantity()).isEqualTo(5);
        verify(allocationRepository).save(allocation);
        verify(lineRepository).save(line);
    }

    private InventoryBatch batch(Long id, ProductSku sku, Location location,
                                 int quantity, int reserved) {
        return InventoryBatch.builder().id(id).batchCode("BATCH-" + id)
            .productSku(sku).location(location).locationCode(location.getLocationCode())
            .quantity(quantity).reservedQuantity(reserved).initialQuantity(quantity)
            .qualityStatus(InventoryQualityStatus.AVAILABLE).active(true).build();
    }
}

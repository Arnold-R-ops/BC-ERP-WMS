package com.wms.system.service;

import com.wms.system.entity.Location;
import com.wms.system.entity.ReceivingTask;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.ReceivingTaskStatus;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ReceiptEventRepository;
import com.wms.system.repository.ReceivingTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceivingTaskServiceTest {

    @Mock private ReceivingTaskRepository receivingTaskRepository;
    @Mock private ReceiptEventRepository receiptEventRepository;
    @Mock private LocationRepository locationRepository;

    private ReceivingTaskService service;

    @BeforeEach
    void setUp() {
        service = new ReceivingTaskService(
            receivingTaskRepository,
            receiptEventRepository,
            locationRepository,
            new ReceivingResponseMapper()
        );
    }

    @Test
    void listsOnlyRepositoryApprovedLocationsForTaskWarehouse() {
        Warehouse warehouse = Warehouse.builder()
            .id(8L).code("WH8").name("Main").build();
        ReceivingTask task = ReceivingTask.builder()
            .id(5L).taskNumber("RCV-5").warehouse(warehouse).build();
        Location location = Location.builder()
            .id(12L).warehouse(warehouse).locationCode("WH8-ZONE_A-A-01-001")
            .enabled(true).build();
        when(receivingTaskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(locationRepository.findByWarehouseIdAndEnabledTrueOrderByLocationCodeAsc(8L))
            .thenReturn(List.of(location));

        var response = service.listEnabledLocations(5L);

        assertThat(response).containsExactly(new com.wms.system.dto.receiving.ReceivingTaskLocationResponse(
            12L, "WH8-ZONE_A-A-01-001", "WH8-ZONE_A-A-01-001"));
        verify(locationRepository)
            .findByWarehouseIdAndEnabledTrueOrderByLocationCodeAsc(8L);
    }

    @Test
    void emptyWarehouseScopeNeverFallsBackToTenantWideTaskQuery() {
        assertThat(service.list(ReceivingTaskStatus.PENDING, List.of())).isEmpty();

        verifyNoInteractions(receivingTaskRepository);
    }

    @Test
    void scopedListWithoutStatusPushesWarehouseIdsIntoRepository() {
        ReceivingTask task = task(5L, 8L, ReceivingTaskStatus.PENDING);
        when(receivingTaskRepository.findByWarehouseIdInOrderByIdDesc(List.of(8L)))
            .thenReturn(List.of(task));

        var response = service.list(null, List.of(8L));

        assertThat(response).singleElement().satisfies(item ->
            assertThat(item.warehouseId()).isEqualTo(8L));
        verify(receivingTaskRepository).findByWarehouseIdInOrderByIdDesc(List.of(8L));
    }

    @Test
    void scopedListWithStatusPushesBothConstraintsIntoRepository() {
        ReceivingTask task = task(5L, 8L, ReceivingTaskStatus.PENDING);
        when(receivingTaskRepository.findByStatusAndWarehouseIdInOrderByIdDesc(
            ReceivingTaskStatus.PENDING, List.of(8L))).thenReturn(List.of(task));

        assertThat(service.list(ReceivingTaskStatus.PENDING, List.of(8L)))
            .hasSize(1);
        verify(receivingTaskRepository).findByStatusAndWarehouseIdInOrderByIdDesc(
            ReceivingTaskStatus.PENDING, List.of(8L));
    }

    private ReceivingTask task(Long id, Long warehouseId, ReceivingTaskStatus status) {
        return ReceivingTask.builder()
            .id(id)
            .taskNumber("RCV-" + id)
            .warehouse(Warehouse.builder()
                .id(warehouseId).code("WH" + warehouseId).name("Warehouse").build())
            .status(status)
            .build();
    }
}

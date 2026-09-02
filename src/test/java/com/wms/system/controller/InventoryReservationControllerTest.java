package com.wms.system.controller;

import com.wms.system.dto.inventory.InventoryReservationResponse;
import com.wms.system.service.InventoryReservationService;
import com.wms.system.service.WarehouseScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InventoryReservationControllerTest {

    @Mock private InventoryReservationService reservationService;
    @Mock private WarehouseScopeService warehouseScopeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(warehouseScopeService.filterAccessible(
            nullable(Authentication.class),
            anyList(),
            any()
        )).thenAnswer(invocation -> {
            List<InventoryReservationResponse> resources = invocation.getArgument(1);
            Function<InventoryReservationResponse, java.util.Collection<Long>> extractor =
                invocation.getArgument(2);
            return resources.stream()
                .filter(resource -> extractor.apply(resource).contains(5L))
                .toList();
        });
        mockMvc = MockMvcBuilders
            .standaloneSetup(new InventoryReservationController(
                reservationService,
                warehouseScopeService
            ))
            .build();
    }

    @Test
    void publicSingleReservationReleaseRouteIsNotMapped() throws Exception {
        mockMvc.perform(post("/api/inventory/reservations/7/release"))
            .andExpect(status().isNotFound());

        verifyNoMoreInteractions(reservationService);
    }

    @Test
    void reservationQueriesRemainAvailable() throws Exception {
        InventoryReservationResponse response = InventoryReservationResponse.builder()
            .id(7L)
            .salesOrderId(41L)
            .inventoryBatchId(13L)
            .warehouseId(5L)
            .build();
        InventoryReservationResponse inaccessible = InventoryReservationResponse.builder()
            .id(8L)
            .salesOrderId(41L)
            .inventoryBatchId(13L)
            .warehouseId(6L)
            .build();
        when(reservationService.listBySalesOrder(41L)).thenReturn(List.of(response, inaccessible));
        when(reservationService.listByBatch(13L)).thenReturn(List.of(response, inaccessible));

        mockMvc.perform(get("/api/inventory/reservations").param("salesOrderId", "41"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(7))
            .andExpect(jsonPath("$[0].warehouseId").doesNotExist());
        mockMvc.perform(get("/api/inventory/reservations/batch/13"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(7))
            .andExpect(jsonPath("$[0].warehouseId").doesNotExist());

        verify(reservationService).listBySalesOrder(41L);
        verify(reservationService).listByBatch(13L);
        verifyNoMoreInteractions(reservationService);
        verify(warehouseScopeService, times(2)).filterAccessible(
            nullable(Authentication.class),
            anyList(),
            any()
        );
        verifyNoMoreInteractions(warehouseScopeService);
    }
}

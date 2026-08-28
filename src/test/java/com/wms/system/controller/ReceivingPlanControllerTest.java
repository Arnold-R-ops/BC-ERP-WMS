package com.wms.system.controller;

import com.wms.system.dto.receiving.ReceivingPlanResponse;
import com.wms.system.entity.User;
import com.wms.system.entity.enums.ReceivingPlanStatus;
import com.wms.system.security.SecurityUser;
import com.wms.system.service.ReceivingPlanService;
import com.wms.system.service.UserWarehouseService;
import com.wms.system.service.WarehouseScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceivingPlanControllerTest {

    @Test
    void warehouseWorkQueueFiltersPlansButPreservesSafeSkuProjection() {
        ReceivingPlanService planService = mock(ReceivingPlanService.class);
        UserWarehouseService userWarehouseService = mock(UserWarehouseService.class);
        ReceivingPlanController controller = new ReceivingPlanController(
            planService, new WarehouseScopeService(userWarehouseService));
        User user = User.builder()
            .id(9L).username("warehouse-user").password("unused").enabled(true).build();
        SecurityUser principal = new SecurityUser(user);
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
            principal,
            "unused",
            List.of(
                new SimpleGrantedAuthority("ROLE_WAREHOUSE_STAFF"),
                new SimpleGrantedAuthority("receiving:view")
            )
        );
        when(userWarehouseService.getAssignedWarehouseIds(9L)).thenReturn(Set.of(20L));
        ReceivingPlanResponse allowed = response(60L, 20L, "WH20");
        when(planService.list(
            List.of(ReceivingPlanStatus.WAITING_SHIPMENT), Set.of(20L)))
            .thenReturn(List.of(allowed));

        var body = controller.listWorkQueue(
            List.of(ReceivingPlanStatus.WAITING_SHIPMENT), authentication).getBody();

        assertThat(body).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(60L);
            assertThat(response.warehouseName()).isEqualTo("Warehouse 20");
            assertThat(response.items()).singleElement().satisfies(item ->
                assertThat(item.skuCode()).isEqualTo("SKU-30"));
        });
        verify(userWarehouseService).getAssignedWarehouseIds(9L);
    }

    private ReceivingPlanResponse response(Long id, Long warehouseId, String warehouseCode) {
        return new ReceivingPlanResponse(
            id,
            0L,
            "RPL-10-" + warehouseId,
            10L,
            "PO-10",
            warehouseId,
            warehouseCode,
            "Warehouse " + warehouseId,
            ReceivingPlanStatus.WAITING_SHIPMENT,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(new ReceivingPlanResponse.Item(
                50L, 40L, 30L, "SKU-30", "Warehouse item", 12, 5, 7))
        );
    }
}

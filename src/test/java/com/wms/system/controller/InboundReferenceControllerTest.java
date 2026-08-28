package com.wms.system.controller;

import com.wms.system.dto.receiving.InboundReferenceWarehouseResponse;
import com.wms.system.entity.User;
import com.wms.system.security.SecurityUser;
import com.wms.system.service.InboundReferenceService;
import com.wms.system.service.UserWarehouseService;
import com.wms.system.service.WarehouseScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InboundReferenceControllerTest {

    @Test
    void otherInboundWarehouseChoicesHonorAssignedWarehouseScope() {
        InboundReferenceService referenceService = mock(InboundReferenceService.class);
        UserWarehouseService userWarehouseService = mock(UserWarehouseService.class);
        InboundReferenceController controller = new InboundReferenceController(
            referenceService, new WarehouseScopeService(userWarehouseService));
        User user = User.builder()
            .id(9L).username("warehouse-user").password("unused").enabled(true).build();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
            new SecurityUser(user),
            "unused",
            List.of(
                new SimpleGrantedAuthority("ROLE_WAREHOUSE_STAFF"),
                new SimpleGrantedAuthority("other_inbound:manage")
            )
        );
        var assigned = new InboundReferenceWarehouseResponse(8L, "WH8", "Assigned");
        var denied = new InboundReferenceWarehouseResponse(9L, "WH9", "Denied");
        when(referenceService.listActiveWarehouses()).thenReturn(List.of(assigned, denied));
        when(userWarehouseService.getAssignedWarehouseIds(9L)).thenReturn(Set.of(8L));

        var response = controller.otherInboundWarehouses(authentication).getBody();

        assertThat(response).containsExactly(assigned);
    }
}

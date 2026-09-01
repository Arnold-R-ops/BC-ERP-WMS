package com.wms.system.controller;

import com.wms.system.entity.enums.TransferTaskDirection;
import com.wms.system.service.WarehouseScopeService;
import com.wms.system.service.transfer.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferWarehouseControllerTest {

    @Mock private TransferService service;
    @Mock private WarehouseScopeService warehouseScopeService;

    private TransferWarehouseController controller;

    @BeforeEach
    void setUp() {
        controller = new TransferWarehouseController(service, warehouseScopeService);
    }

    @Test
    void listWithoutDirectionShowsOnlyInboundToReceiveOnlyUser() {
        var authentication = new TestingAuthenticationToken("receiver", null, "transfer:receive");
        when(service.listTasks(TransferTaskDirection.INBOUND, null)).thenReturn(List.of());

        controller.list(null, authentication);

        verify(service).listTasks(TransferTaskDirection.INBOUND, null);
        verify(service, never()).listTasks(TransferTaskDirection.OUTBOUND, null);
    }

    @Test
    void receiveOnlyUserCannotRequestOutboundQueue() {
        var authentication = new TestingAuthenticationToken("receiver", null, "transfer:receive");

        assertThatThrownBy(() -> controller.list(TransferTaskDirection.OUTBOUND, authentication))
            .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(service);
    }

    @Test
    void dispatchOnlyUserCannotReadInboundTaskById() {
        var authentication = new TestingAuthenticationToken("dispatcher", null, "transfer:dispatch");
        when(service.warehouseIdForTask(91L)).thenReturn(20L);
        when(service.directionForTask(91L)).thenReturn(TransferTaskDirection.INBOUND);

        assertThatThrownBy(() -> controller.get(91L, authentication))
            .isInstanceOf(AccessDeniedException.class);

        verify(warehouseScopeService).requireAccess(authentication, List.of(20L),
            "TransferWarehouseTask", 91L);
        verify(service, never()).getTask(91L);
    }

    @Test
    void tenantAdminCanListBothDirections() {
        var authentication = new TestingAuthenticationToken("admin", null, "TENANT_ADMIN");
        when(service.listTasks(null, null)).thenReturn(List.of());

        controller.list(null, authentication);

        verify(service).listTasks(null, null);
    }
}

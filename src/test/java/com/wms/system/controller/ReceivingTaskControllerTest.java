package com.wms.system.controller;

import com.wms.system.dto.receiving.ReceiptEventResponse;
import com.wms.system.dto.receiving.SubmitReceivingReceiptRequest;
import com.wms.system.dto.receiving.ReceivingTaskLocationResponse;
import com.wms.system.entity.enums.ReceivingTaskStatus;
import com.wms.system.service.ReceivingReceiptService;
import com.wms.system.service.ReceivingTaskService;
import com.wms.system.service.WarehouseScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ReceivingTaskControllerTest {

    @Test
    void warehouseTaskListPushesAssignedScopeAndStatusIntoService() {
        ReceivingTaskService taskService = mock(ReceivingTaskService.class);
        ReceivingReceiptService receiptService = mock(ReceivingReceiptService.class);
        WarehouseScopeService scopeService = mock(WarehouseScopeService.class);
        ReceivingTaskController controller = new ReceivingTaskController(
            taskService, receiptService, scopeService);
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
            "receiver", "password", "receiving:view");
        when(scopeService.isWarehouseStaff(authentication)).thenReturn(true);
        when(scopeService.getAssignedWarehouseIds(authentication)).thenReturn(Set.of(8L));
        when(taskService.list(ReceivingTaskStatus.PENDING, Set.of(8L)))
            .thenReturn(List.of());

        controller.list(ReceivingTaskStatus.PENDING, authentication);

        verify(taskService).list(ReceivingTaskStatus.PENDING, Set.of(8L));
        verify(taskService, never()).list(ReceivingTaskStatus.PENDING);
    }

    @Test
    void emptyWarehouseTaskScopeNeverUsesTenantWideServicePath() {
        ReceivingTaskService taskService = mock(ReceivingTaskService.class);
        ReceivingReceiptService receiptService = mock(ReceivingReceiptService.class);
        WarehouseScopeService scopeService = mock(WarehouseScopeService.class);
        ReceivingTaskController controller = new ReceivingTaskController(
            taskService, receiptService, scopeService);
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
            "receiver", "password", "receiving:view");
        when(scopeService.isWarehouseStaff(authentication)).thenReturn(true);
        when(scopeService.getAssignedWarehouseIds(authentication)).thenReturn(Set.of());
        when(taskService.list(null, Set.of())).thenReturn(List.of());

        controller.list(null, authentication);

        verify(taskService).list(null, Set.of());
        verify(taskService, never()).list((ReceivingTaskStatus) null);
    }

    @Test
    void taskLocationChoicesAreWarehouseScopedBeforeLoading() {
        ReceivingTaskService taskService = mock(ReceivingTaskService.class);
        ReceivingReceiptService receiptService = mock(ReceivingReceiptService.class);
        WarehouseScopeService scopeService = mock(WarehouseScopeService.class);
        ReceivingTaskController controller = new ReceivingTaskController(
            taskService, receiptService, scopeService);
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
            "receiver", "password", "receiving:view");
        var expected = List.of(new ReceivingTaskLocationResponse(
            12L, "WH8-ZONE_A-A-01-001", "WH8-ZONE_A-A-01-001"));
        when(taskService.getWarehouseId(5L)).thenReturn(8L);
        when(taskService.listEnabledLocations(5L)).thenReturn(expected);

        var actual = controller.listLocations(5L, authentication).getBody();

        assertEquals(expected, actual);
        verify(scopeService).requireAccess(authentication, List.of(8L), "ReceivingTask", 5L);
        verify(taskService).listEnabledLocations(5L);
    }

    @Test
    void forwardsRequiredIdempotencyKeyAndServerResolvedActor() {
        ReceivingTaskService taskService = mock(ReceivingTaskService.class);
        ReceivingReceiptService receiptService = mock(ReceivingReceiptService.class);
        WarehouseScopeService scopeService = mock(WarehouseScopeService.class);
        ReceivingTaskController controller = new ReceivingTaskController(
            taskService, receiptService, scopeService);
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
            "receiver", "password", "receiving:execute");
        when(taskService.getWarehouseId(5L)).thenReturn(8L);
        SubmitReceivingReceiptRequest request = new SubmitReceivingReceiptRequest(
            "ok", List.of(new SubmitReceivingReceiptRequest.Line(
                10L, 12L, 2, null, null, null)));
        ReceiptEventResponse expected = new ReceiptEventResponse(
            1L, "RCE-1", 5L, "RCV-5", LocalDateTime.now(), "receiver", "ok", List.of());
        when(receiptService.receive(5L, "receipt-key", request, 0L, "receiver"))
            .thenReturn(expected);

        ReceiptEventResponse actual = controller.receive(
            5L, "receipt-key", request, authentication).getBody();

        assertSame(expected, actual);
        verify(scopeService).requireAccess(authentication, List.of(8L), "ReceivingTask", 5L);
        verify(receiptService).receive(5L, "receipt-key", request, 0L, "receiver");
    }
}

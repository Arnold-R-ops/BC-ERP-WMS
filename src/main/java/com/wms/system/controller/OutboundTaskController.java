package com.wms.system.controller;

import com.wms.system.dto.outbound.ConfirmPickingRequest;
import com.wms.system.dto.outbound.OutboundTaskResponse;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.OutboundService;
import com.wms.system.service.WarehouseScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/outbound-tasks")
@RequiredArgsConstructor
public class OutboundTaskController {

    private final OutboundService outboundService;
    private final WarehouseScopeService warehouseScopeService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('outbound:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<OutboundTaskResponse>> listOutboundTasks(
        @RequestParam(value = "salesOrderId", required = false) Long salesOrderId,
        @RequestParam(value = "status", required = false) String status,
        Authentication authentication
    ) {
        log.info("API call: listOutboundTasks - salesOrderId={}, status={}", salesOrderId, status);
        List<OutboundTaskResponse> responses = outboundService.listOutboundTasks(salesOrderId, status);
        return ResponseEntity.ok(warehouseScopeService.filterAccessible(
            authentication,
            responses,
            response -> java.util.Collections.singletonList(response.getWarehouseId())
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('outbound:view', 'TENANT_ADMIN')")
    public ResponseEntity<OutboundTaskResponse> getOutboundTask(
        @PathVariable("id") Long id,
        Authentication authentication
    ) {
        log.info("API call: getOutboundTask - id={}", id);
        OutboundTaskResponse response = outboundService.getOutboundTask(id);
        requireTaskAccess(authentication, response);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyAuthority('outbound:pick', 'TENANT_ADMIN')")
    public ResponseEntity<OutboundTaskResponse> confirmPicking(
        @PathVariable("id") Long id,
        @Valid @RequestBody ConfirmPickingRequest request,
        Authentication authentication
    ) {
        Long userId = AuthUserResolver.resolveUserId(authentication);
        String username = AuthUserResolver.resolveUsername(authentication);

        log.info("API call: confirmPicking - taskId={}, actualQty={}, operator={}",
            id, request.getActualQty(), username);

        if (warehouseScopeService.isWarehouseStaff(authentication)) {
            requireTaskAccess(authentication, outboundService.getOutboundTask(id));
        }

        OutboundTaskResponse response = outboundService.confirmPicking(
            id,
            request.getActualQty(),
            request.getLocationId(),
            request.getSkuCode(),
            request.getBatchCode(),
            userId,
            username
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/batch-confirm")
    @PreAuthorize("hasAnyAuthority('outbound:pick', 'TENANT_ADMIN')")
    public ResponseEntity<List<OutboundTaskResponse>> batchConfirmPicking(
        @RequestBody List<Long> taskIds,
        Authentication authentication
    ) {
        Long userId = AuthUserResolver.resolveUserId(authentication);
        String username = AuthUserResolver.resolveUsername(authentication);

        log.info("API call: batchConfirmPicking - taskIds={}, operator={}", taskIds, username);

        if (warehouseScopeService.isWarehouseStaff(authentication)) {
            taskIds.stream()
                .map(outboundService::getOutboundTask)
                .forEach(task -> requireTaskAccess(authentication, task));
        }

        List<OutboundTaskResponse> responses = outboundService.batchConfirmPicking(
            taskIds,
            userId,
            username
        );

        return ResponseEntity.ok(responses);
    }

    private void requireTaskAccess(Authentication authentication, OutboundTaskResponse task) {
        warehouseScopeService.requireAccess(
            authentication,
            java.util.Collections.singletonList(task.getWarehouseId()),
            "OUTBOUND_TASK",
            task.getId()
        );
    }
}

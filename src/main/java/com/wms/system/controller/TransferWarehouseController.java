package com.wms.system.controller;

import com.wms.system.dto.transfer.*;
import com.wms.system.entity.enums.TransferTaskDirection;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.WarehouseScopeService;
import com.wms.system.service.transfer.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/transfer-warehouse/tasks")
@RequiredArgsConstructor
public class TransferWarehouseController {
    private final TransferService service;
    private final WarehouseScopeService warehouseScopeService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('transfer:dispatch', 'transfer:receive', 'TENANT_ADMIN')")
    public List<TransferWarehouseTaskResponse> list(
        @RequestParam(required = false) TransferTaskDirection direction,
        Authentication authentication
    ) {
        if (direction != null) {
            requireDirectionAuthority(authentication, direction);
            return service.listTasks(direction, allowedWarehouses(authentication));
        }
        boolean outbound = mayUse(authentication, TransferTaskDirection.OUTBOUND);
        boolean inbound = mayUse(authentication, TransferTaskDirection.INBOUND);
        if (outbound && inbound) {
            return service.listTasks(null, allowedWarehouses(authentication));
        }
        return service.listTasks(outbound ? TransferTaskDirection.OUTBOUND : TransferTaskDirection.INBOUND,
            allowedWarehouses(authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('transfer:dispatch', 'transfer:receive', 'TENANT_ADMIN')")
    public TransferWarehouseTaskResponse get(@PathVariable Long id, Authentication authentication) {
        requireAccess(authentication, id);
        requireDirectionAuthority(authentication, service.directionForTask(id));
        return service.getTask(id);
    }

    @GetMapping("/{id}/locations")
    @PreAuthorize("hasAnyAuthority('transfer:receive', 'TENANT_ADMIN')")
    public List<TransferLocationResponse> locations(@PathVariable Long id, Authentication authentication) {
        requireAccess(authentication, id);
        return service.locations(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('transfer:dispatch', 'transfer:receive', 'TENANT_ADMIN')")
    public TransferWarehouseTaskResponse update(@PathVariable Long id,
                                                 @RequestHeader("Idempotency-Key") String key,
                                                 @Valid @RequestBody UpdateTransferTaskRequest request,
                                                 Authentication authentication) {
        requireAccess(authentication, id);
        requireDirectionAuthority(authentication, service.directionForTask(id));
        return service.updateTask(id, request, key, actorId(authentication), actorName(authentication));
    }

    @PostMapping("/{id}/dispatches")
    @PreAuthorize("hasAnyAuthority('transfer:dispatch', 'TENANT_ADMIN')")
    public ResponseEntity<TransferWarehouseTaskResponse> dispatch(
        @PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody DispatchTransferRequest request, Authentication authentication
    ) {
        requireAccess(authentication, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.dispatch(
            id, request, key, actorId(authentication), actorName(authentication)));
    }

    @PostMapping("/{id}/receipts")
    @PreAuthorize("hasAnyAuthority('transfer:receive', 'TENANT_ADMIN')")
    public ResponseEntity<TransferWarehouseTaskResponse> receive(
        @PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody ReceiveTransferRequest request, Authentication authentication
    ) {
        requireAccess(authentication, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.receive(
            id, request, key, actorId(authentication), actorName(authentication)));
    }

    private void requireAccess(Authentication authentication, Long taskId) {
        warehouseScopeService.requireAccess(authentication, List.of(service.warehouseIdForTask(taskId)),
            "TransferWarehouseTask", taskId);
    }

    private Set<Long> allowedWarehouses(Authentication authentication) {
        return warehouseScopeService.isWarehouseStaff(authentication)
            ? warehouseScopeService.getAssignedWarehouseIds(authentication) : null;
    }

    private void requireDirectionAuthority(Authentication authentication, TransferTaskDirection direction) {
        if (!mayUse(authentication, direction)) {
            throw new AccessDeniedException("No permission for " + direction + " transfer tasks");
        }
    }

    private boolean mayUse(Authentication authentication, TransferTaskDirection direction) {
        String required = direction == TransferTaskDirection.OUTBOUND ? "transfer:dispatch" : "transfer:receive";
        return authentication.getAuthorities().stream().anyMatch(authority ->
            "TENANT_ADMIN".equals(authority.getAuthority()) || required.equals(authority.getAuthority()));
    }

    private Long actorId(Authentication authentication) {
        return AuthUserResolver.resolveUserId(authentication);
    }

    private String actorName(Authentication authentication) {
        return AuthUserResolver.resolveUsername(authentication);
    }
}

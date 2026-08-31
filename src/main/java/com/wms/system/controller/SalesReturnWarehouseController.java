package com.wms.system.controller;

import com.wms.system.dto.rma.*;
import com.wms.system.entity.enums.RmaTaskStatus;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.rma.RmaService;
import com.wms.system.service.WarehouseScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/rma-warehouse/tasks")
@RequiredArgsConstructor
public class SalesReturnWarehouseController {
    private final RmaService service;
    private final WarehouseScopeService warehouseScopeService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('rma:receive', 'rma:inspect', 'TENANT_ADMIN')")
    public List<RmaWarehouseTaskResponse> list(@RequestParam(required = false) RmaTaskStatus status,
                                               Authentication authentication) {
        return service.listTasks(status, allowedWarehouses(authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('rma:receive', 'rma:inspect', 'TENANT_ADMIN')")
    public RmaWarehouseTaskResponse get(@PathVariable Long id, Authentication authentication) {
        requireAccess(authentication, id);
        return service.getTask(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('rma:receive', 'TENANT_ADMIN')")
    public RmaWarehouseTaskResponse update(@PathVariable Long id,
                                           @RequestHeader("Idempotency-Key") String key,
                                           @Valid @RequestBody UpdateRmaTaskRequest request,
                                           Authentication authentication) {
        requireAccess(authentication, id);
        return service.updateTask(id, request, key, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication));
    }

    @PostMapping("/{id}/receipts")
    @PreAuthorize("hasAnyAuthority('rma:receive', 'TENANT_ADMIN')")
    public ResponseEntity<RmaWarehouseTaskResponse> receive(@PathVariable Long id,
                                                            @RequestHeader("Idempotency-Key") String key,
                                                            @Valid @RequestBody ReceiveRmaRequest request,
                                                            Authentication authentication) {
        requireAccess(authentication, id);
        service.receive(id, request, key, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(service.getTask(id));
    }

    @PostMapping("/{id}/inspections")
    @PreAuthorize("hasAnyAuthority('rma:inspect', 'TENANT_ADMIN')")
    public ResponseEntity<RmaWarehouseTaskResponse> inspect(@PathVariable Long id,
                                                            @RequestHeader("Idempotency-Key") String key,
                                                            @Valid @RequestBody InspectRmaRequest request,
                                                            Authentication authentication) {
        requireAccess(authentication, id);
        service.inspect(id, request, key, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(service.getTask(id));
    }

    private void requireAccess(Authentication authentication, Long taskId) {
        warehouseScopeService.requireAccess(authentication, List.of(service.warehouseIdForTask(taskId)),
            "SalesReturnTask", taskId);
    }

    private Set<Long> allowedWarehouses(Authentication authentication) {
        return warehouseScopeService.isWarehouseStaff(authentication)
            ? warehouseScopeService.getAssignedWarehouseIds(authentication) : null;
    }
}

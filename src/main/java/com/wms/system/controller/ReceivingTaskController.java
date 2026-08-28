package com.wms.system.controller;

import com.wms.system.dto.receiving.ReceiptEventResponse;
import com.wms.system.dto.receiving.ReceivingTaskResponse;
import com.wms.system.dto.receiving.ReceivingTaskLocationResponse;
import com.wms.system.dto.receiving.SubmitReceivingReceiptRequest;
import com.wms.system.entity.enums.ReceivingTaskStatus;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.ReceivingReceiptService;
import com.wms.system.service.ReceivingTaskService;
import com.wms.system.service.WarehouseScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/receiving-tasks")
@RequiredArgsConstructor
public class ReceivingTaskController {

    private final ReceivingTaskService receivingTaskService;
    private final ReceivingReceiptService receivingReceiptService;
    private final WarehouseScopeService warehouseScopeService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('receiving:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<ReceivingTaskResponse>> list(
        @RequestParam(required = false) ReceivingTaskStatus status,
        Authentication authentication
    ) {
        if (warehouseScopeService.isWarehouseStaff(authentication)) {
            return ResponseEntity.ok(receivingTaskService.list(
                status, warehouseScopeService.getAssignedWarehouseIds(authentication)));
        }
        return ResponseEntity.ok(receivingTaskService.list(status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('receiving:view', 'TENANT_ADMIN')")
    public ResponseEntity<ReceivingTaskResponse> get(
        @PathVariable Long id,
        Authentication authentication
    ) {
        requireTaskAccess(authentication, id);
        return ResponseEntity.ok(receivingTaskService.get(id));
    }

    @GetMapping("/{id}/receipts")
    @PreAuthorize("hasAnyAuthority('receiving:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<ReceiptEventResponse>> listReceipts(
        @PathVariable Long id,
        Authentication authentication
    ) {
        requireTaskAccess(authentication, id);
        return ResponseEntity.ok(receivingTaskService.listReceipts(id));
    }

    @GetMapping("/{id}/locations")
    @PreAuthorize("hasAnyAuthority('receiving:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<ReceivingTaskLocationResponse>> listLocations(
        @PathVariable Long id,
        Authentication authentication
    ) {
        requireTaskAccess(authentication, id);
        return ResponseEntity.ok(receivingTaskService.listEnabledLocations(id));
    }

    @PostMapping("/{id}/receipts")
    @PreAuthorize("hasAnyAuthority('receiving:execute', 'TENANT_ADMIN')")
    public ResponseEntity<ReceiptEventResponse> receive(
        @PathVariable Long id,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody SubmitReceivingReceiptRequest request,
        Authentication authentication
    ) {
        requireTaskAccess(authentication, id);
        return ResponseEntity.ok(receivingReceiptService.receive(
            id, idempotencyKey, request, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    private void requireTaskAccess(Authentication authentication, Long taskId) {
        warehouseScopeService.requireAccess(
            authentication, List.of(receivingTaskService.getWarehouseId(taskId)),
            "ReceivingTask", taskId);
    }
}

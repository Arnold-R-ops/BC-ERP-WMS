package com.wms.system.controller;

import com.wms.system.dto.receiving.ReceivingPlanResponse;
import com.wms.system.dto.receiving.UpdateReceivingPlanOperationsRequest;
import com.wms.system.entity.enums.ReceivingPlanStatus;
import com.wms.system.service.ReceivingPlanService;
import com.wms.system.service.WarehouseScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReceivingPlanController {

    private final ReceivingPlanService receivingPlanService;
    private final WarehouseScopeService warehouseScopeService;

    @GetMapping("/receiving-plans")
    @PreAuthorize("hasAnyAuthority('receiving:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<ReceivingPlanResponse>> listWorkQueue(
        @RequestParam(name = "status", required = false) List<ReceivingPlanStatus> statuses,
        Authentication authentication
    ) {
        if (warehouseScopeService.isWarehouseStaff(authentication)) {
            return ResponseEntity.ok(receivingPlanService.list(
                statuses, warehouseScopeService.getAssignedWarehouseIds(authentication)));
        }
        return ResponseEntity.ok(receivingPlanService.list(statuses));
    }

    @GetMapping("/purchase-orders/{purchaseOrderId}/receiving-plans")
    @PreAuthorize("hasAnyAuthority('receiving:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<ReceivingPlanResponse>> list(
        @PathVariable Long purchaseOrderId,
        Authentication authentication
    ) {
        if (warehouseScopeService.isWarehouseStaff(authentication)) {
            return ResponseEntity.ok(receivingPlanService.listForPurchaseOrder(
                purchaseOrderId,
                warehouseScopeService.getAssignedWarehouseIds(authentication)
            ));
        }
        return ResponseEntity.ok(receivingPlanService.listForPurchaseOrder(purchaseOrderId));
    }

    @PutMapping("/receiving-plans/{id}/operations")
    @PreAuthorize("hasAnyAuthority('receiving:plan:edit', 'TENANT_ADMIN')")
    public ResponseEntity<ReceivingPlanResponse> updateOperations(
        @PathVariable Long id,
        @Valid @RequestBody UpdateReceivingPlanOperationsRequest request,
        Authentication authentication
    ) {
        warehouseScopeService.requireAccess(
            authentication, List.of(receivingPlanService.getWarehouseId(id)), "ReceivingPlan", id);
        ReceivingPlanResponse response = receivingPlanService.updateOperations(id, request);
        return ResponseEntity.ok(response);
    }
}

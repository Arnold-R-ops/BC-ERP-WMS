package com.wms.system.controller;

import com.wms.system.dto.purchase.PurchaseOrderWorkflowActionRequest;
import com.wms.system.dto.purchase.PurchaseOrderWorkflowResponse;
import com.wms.system.entity.PurchaseOrder;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchase-orders/{purchaseOrderId}")
@RequiredArgsConstructor
public class PurchaseOrderWorkflowController {
    private final PurchaseOrderService purchaseOrderService;

    @PostMapping("/submit")
    @PreAuthorize("hasAnyAuthority('purchase:submit', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseOrderWorkflowResponse> submit(
        @PathVariable Long purchaseOrderId,
        @Valid @RequestBody PurchaseOrderWorkflowActionRequest request,
        Authentication authentication
    ) {
        return response(purchaseOrderService.submit(
            purchaseOrderId, request.getVersion(), actorId(authentication), actorName(authentication)));
    }

    @PostMapping("/approve")
    @PreAuthorize("hasAnyAuthority('purchase:approve', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseOrderWorkflowResponse> approve(
        @PathVariable Long purchaseOrderId,
        @Valid @RequestBody PurchaseOrderWorkflowActionRequest request,
        Authentication authentication
    ) {
        return response(purchaseOrderService.approve(
            purchaseOrderId, request.getVersion(), actorId(authentication), actorName(authentication)));
    }

    @PostMapping("/reject")
    @PreAuthorize("hasAnyAuthority('purchase:approve', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseOrderWorkflowResponse> reject(
        @PathVariable Long purchaseOrderId,
        @Valid @RequestBody PurchaseOrderWorkflowActionRequest request,
        Authentication authentication
    ) {
        return response(purchaseOrderService.reject(
            purchaseOrderId, request.getVersion(), requiredReason(request),
            actorId(authentication), actorName(authentication)));
    }

    @PostMapping("/withdraw")
    @PreAuthorize("hasAnyAuthority('purchase:submit', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseOrderWorkflowResponse> withdraw(
        @PathVariable Long purchaseOrderId,
        @Valid @RequestBody PurchaseOrderWorkflowActionRequest request,
        Authentication authentication
    ) {
        return response(purchaseOrderService.withdrawBeforeShipment(
            purchaseOrderId, request.getVersion(), requiredReason(request),
            actorId(authentication), actorName(authentication)));
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasAnyAuthority('purchase:cancel', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseOrderWorkflowResponse> cancel(
        @PathVariable Long purchaseOrderId,
        @Valid @RequestBody PurchaseOrderWorkflowActionRequest request,
        Authentication authentication
    ) {
        return response(purchaseOrderService.cancelGoverned(
            purchaseOrderId, request.getVersion(), requiredReason(request),
            actorId(authentication), actorName(authentication)));
    }

    @PostMapping("/void")
    @PreAuthorize("hasAnyAuthority('purchase:void', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseOrderWorkflowResponse> voidOrder(
        @PathVariable Long purchaseOrderId,
        @Valid @RequestBody PurchaseOrderWorkflowActionRequest request,
        Authentication authentication
    ) {
        return response(purchaseOrderService.voidGoverned(
            purchaseOrderId, request.getVersion(), requiredReason(request),
            actorId(authentication), actorName(authentication)));
    }

    private ResponseEntity<PurchaseOrderWorkflowResponse> response(PurchaseOrder order) {
        return ResponseEntity.ok(PurchaseOrderWorkflowResponse.from(order));
    }

    private Long actorId(Authentication authentication) {
        return AuthUserResolver.resolveUserId(authentication);
    }

    private String actorName(Authentication authentication) {
        return AuthUserResolver.resolveUsername(authentication);
    }

    private String requiredReason(PurchaseOrderWorkflowActionRequest request) {
        return request.getReason();
    }
}

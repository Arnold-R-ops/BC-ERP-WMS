package com.wms.system.controller;

import com.wms.system.dto.integration.ShopifyReconciliationRepairRequest;
import com.wms.system.dto.integration.ShopifyReconciliationRepairResult;
import com.wms.system.dto.integration.ShopifyReconciliationReport;
import com.wms.system.dto.integration.ShopifyReconciliationRequest;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.ShopifyReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integration/shopify/reconcile")
@RequiredArgsConstructor
public class ShopifyReconciliationController {

    private final ShopifyReconciliationService reconciliationService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('integration:reconcile:view', 'SUPER_ADMIN')")
    public ResponseEntity<ShopifyReconciliationReport> reconcile(
        @Valid @RequestBody ShopifyReconciliationRequest request
    ) {
        return ResponseEntity.ok(reconciliationService.reconcile(request));
    }

    @PostMapping("/repair")
    @PreAuthorize("hasAnyAuthority('integration:reconcile:repair', 'SUPER_ADMIN')")
    public ResponseEntity<ShopifyReconciliationRepairResult> repair(
        @Valid @RequestBody ShopifyReconciliationRepairRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(reconciliationService.repair(
            request.getRawEventIds(),
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)
        ));
    }
}

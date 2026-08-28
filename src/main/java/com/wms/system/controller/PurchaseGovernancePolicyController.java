package com.wms.system.controller;

import com.wms.system.dto.purchase.PurchaseGovernancePolicyResponse;
import com.wms.system.dto.purchase.PurchaseGovernancePolicyUpdateRequest;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.PurchaseGovernancePolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchase-governance-policy")
@RequiredArgsConstructor
public class PurchaseGovernancePolicyController {
    private final PurchaseGovernancePolicyService policyService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('purchase:view', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseGovernancePolicyResponse> getPolicy() {
        return ResponseEntity.ok(policyService.getPolicy());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    public ResponseEntity<PurchaseGovernancePolicyResponse> updatePolicy(
        @Valid @RequestBody PurchaseGovernancePolicyUpdateRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(policyService.updatePolicy(
            request.getPurchaseApprovalMode(),
            request.getShortReceiptClosureMode(),
            request.getReason(),
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)
        ));
    }
}

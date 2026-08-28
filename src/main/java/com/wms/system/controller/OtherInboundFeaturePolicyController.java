package com.wms.system.controller;

import com.wms.system.dto.inbound.OtherInboundFeaturePolicyResponse;
import com.wms.system.dto.inbound.OtherInboundFeaturePolicyUpdateRequest;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.OtherInboundFeaturePolicyService;
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
@RequestMapping("/api/other-inbound-policy")
@RequiredArgsConstructor
public class OtherInboundFeaturePolicyController {
    private final OtherInboundFeaturePolicyService policyService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('other_inbound:manage', 'TENANT_ADMIN')")
    public ResponseEntity<OtherInboundFeaturePolicyResponse> getPolicy() {
        return ResponseEntity.ok(policyService.getPolicy());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    public ResponseEntity<OtherInboundFeaturePolicyResponse> updatePolicy(
        @Valid @RequestBody OtherInboundFeaturePolicyUpdateRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(policyService.updatePolicy(
            request.isEnabled(),
            request.getAllowedBusinessTypes(),
            request.getReason(),
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)
        ));
    }
}

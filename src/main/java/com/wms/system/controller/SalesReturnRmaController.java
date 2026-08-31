package com.wms.system.controller;

import com.wms.system.dto.rma.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.SalesCommandIdempotencyService;
import com.wms.system.service.rma.RmaPolicyService;
import com.wms.system.service.rma.RmaChannelCaseService;
import com.wms.system.service.rma.RmaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rmas")
@RequiredArgsConstructor
public class SalesReturnRmaController {
    private final RmaService service;
    private final RmaPolicyService policyService;
    private final RmaChannelCaseService channelCaseService;
    private final SalesCommandIdempotencyService commandService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('rma:view', 'TENANT_ADMIN')")
    public List<RmaResponse> list(@RequestParam(required = false) Long salesOrderId) {
        return service.list(salesOrderId);
    }

    @GetMapping("/channel-cases")
    @PreAuthorize("hasAnyAuthority('rma:view', 'TENANT_ADMIN')")
    public List<RmaChannelCaseResponse> channelCases() {
        return channelCaseService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('rma:view', 'TENANT_ADMIN')")
    public RmaResponse get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('rma:create', 'TENANT_ADMIN')")
    public ResponseEntity<RmaResponse> create(
        @RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody CreateRmaRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, key,
            AuthUserResolver.resolveUserId(authentication), AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('rma:approve', 'TENANT_ADMIN')")
    public RmaResponse approve(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
                               @Valid @RequestBody RmaDecisionRequest request, Authentication authentication) {
        return service.approve(id, request, key, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('rma:approve', 'TENANT_ADMIN')")
    public RmaResponse reject(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
                              @Valid @RequestBody RmaDecisionRequest request, Authentication authentication) {
        return service.reject(id, request, key, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('rma:create', 'TENANT_ADMIN')")
    public RmaResponse cancel(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
                              @Valid @RequestBody RmaReasonRequest request, Authentication authentication) {
        return service.cancel(id, request, key, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication));
    }

    @PostMapping("/{id}/close-remainder")
    @PreAuthorize("hasAnyAuthority('rma:create', 'TENANT_ADMIN')")
    public RmaResponse close(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
                             @Valid @RequestBody RmaReasonRequest request, Authentication authentication) {
        return service.closeRemainder(id, request, key, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication));
    }

    @PostMapping("/{id}/resolution")
    @PreAuthorize("hasAnyAuthority('rma:resolve', 'TENANT_ADMIN')")
    public ResponseEntity<RmaResponse> resolve(@PathVariable Long id,
                                               @RequestHeader("Idempotency-Key") String key,
                                               @Valid @RequestBody RequestRmaResolutionRequest request,
                                               Authentication authentication) {
        if (request.advance() && !has(authentication, "rma:advance-resolution")
            && !has(authentication, "TENANT_ADMIN")) {
            throw new BusinessException(ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("requiredPermission", "rma:advance-resolution"));
        }
        return ResponseEntity.accepted().body(service.resolve(id, request, key,
            AuthUserResolver.resolveUserId(authentication), AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/{id}/refund-eligibility")
    @PreAuthorize("hasAnyAuthority('rma:resolve', 'TENANT_ADMIN')")
    public RmaResponse setRefundEligibility(@PathVariable Long id,
                                            @RequestHeader("Idempotency-Key") String key,
                                            @Valid @RequestBody SetRmaRefundEligibilityRequest request,
                                            Authentication authentication) {
        return service.setRefundEligibility(id, request, key,
            AuthUserResolver.resolveUserId(authentication), AuthUserResolver.resolveUsername(authentication));
    }

    @PostMapping("/{id}/refund-confirmation")
    @PreAuthorize("hasAnyAuthority('rma:resolve', 'TENANT_ADMIN')")
    public RmaResponse confirmRefund(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
                                     @Valid @RequestBody ConfirmRmaRefundRequest request,
                                     Authentication authentication) {
        return service.confirmRefund(id, request, key, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication));
    }

    @GetMapping("/policy")
    @PreAuthorize("hasAnyAuthority('rma:view', 'rma:create', 'rma:approve', 'rma:resolve', 'rma:policy', 'TENANT_ADMIN')")
    public RmaPolicyResponse policy() { return policyService.get(); }

    @PutMapping("/policy")
    @PreAuthorize("hasAnyAuthority('rma:policy', 'TENANT_ADMIN')")
    public RmaPolicyResponse updatePolicy(@RequestHeader("Idempotency-Key") String key,
                                          @Valid @RequestBody UpdateRmaPolicyRequest request,
                                          Authentication authentication) {
        Long actorId = AuthUserResolver.resolveUserId(authentication);
        String actorName = AuthUserResolver.resolveUsername(authentication);
        return commandService.execute(key, "RMA_POLICY_UPDATE", request, actorId, actorName,
            RmaPolicyResponse.class, () -> policyService.update(request, actorId, actorName));
    }

    private boolean has(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(item -> authority.equals(item.getAuthority()));
    }
}

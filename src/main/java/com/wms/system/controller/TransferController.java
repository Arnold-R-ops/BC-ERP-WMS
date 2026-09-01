package com.wms.system.controller;

import com.wms.system.dto.transfer.*;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.SalesCommandIdempotencyService;
import com.wms.system.service.transfer.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {
    private final TransferService service;
    private final TransferPolicyService policyService;
    private final SalesCommandIdempotencyService commandService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('transfer:view', 'transfer:create', 'transfer:approve', 'transfer:close', 'transfer:policy', 'TENANT_ADMIN')")
    public List<TransferResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('transfer:view', 'transfer:create', 'transfer:approve', 'transfer:close', 'transfer:policy', 'TENANT_ADMIN')")
    public TransferResponse get(@PathVariable Long id) { return service.get(id); }

    @GetMapping("/references")
    @PreAuthorize("hasAnyAuthority('transfer:view', 'transfer:create', 'TENANT_ADMIN')")
    public TransferReferencesResponse references() { return service.references(); }

    @GetMapping("/batches")
    @PreAuthorize("hasAnyAuthority('transfer:view', 'transfer:create', 'TENANT_ADMIN')")
    public List<TransferBatchCandidateResponse> batches(@RequestParam Long warehouseId,
                                                        @RequestParam Long productSkuId) {
        return service.batches(warehouseId, productSkuId);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('transfer:create', 'TENANT_ADMIN')")
    public ResponseEntity<TransferResponse> create(@RequestHeader("Idempotency-Key") String key,
                                                    @Valid @RequestBody CreateTransferRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, key,
            actorId(authentication), actorName(authentication)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('transfer:create', 'TENANT_ADMIN')")
    public TransferResponse update(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
                                   @Valid @RequestBody UpdateTransferRequest request,
                                   Authentication authentication) {
        return service.update(id, request, key, actorId(authentication), actorName(authentication));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('transfer:create', 'TENANT_ADMIN')")
    public TransferResponse submit(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
                                   @Valid @RequestBody TransferDecisionRequest request,
                                   Authentication authentication) {
        return service.submit(id, request, key, actorId(authentication), actorName(authentication));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('transfer:approve', 'TENANT_ADMIN')")
    public TransferResponse approve(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
                                    @Valid @RequestBody TransferDecisionRequest request,
                                    Authentication authentication) {
        return service.approve(id, request, key, actorId(authentication), actorName(authentication));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('transfer:approve', 'TENANT_ADMIN')")
    public TransferResponse reject(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
                                   @Valid @RequestBody TransferDecisionRequest request,
                                   Authentication authentication) {
        return service.reject(id, request, key, actorId(authentication), actorName(authentication));
    }

    @PostMapping("/{id}/reallocate")
    @PreAuthorize("hasAnyAuthority('transfer:create', 'TENANT_ADMIN')")
    public TransferResponse reallocate(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
                                       @Valid @RequestBody TransferDecisionRequest request,
                                       Authentication authentication) {
        return service.reallocate(id, request, key, actorId(authentication), actorName(authentication));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('transfer:create', 'TENANT_ADMIN')")
    public TransferResponse cancel(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
                                   @Valid @RequestBody TransferReasonRequest request,
                                   Authentication authentication) {
        return service.cancel(id, request, key, actorId(authentication), actorName(authentication));
    }

    @PostMapping("/{id}/close-remainder")
    @PreAuthorize("hasAnyAuthority('transfer:close', 'TENANT_ADMIN')")
    public TransferResponse closeRemainder(@PathVariable Long id,
                                           @RequestHeader("Idempotency-Key") String key,
                                           @Valid @RequestBody TransferReasonRequest request,
                                           Authentication authentication) {
        return service.closeRemainder(id, request, key, actorId(authentication), actorName(authentication));
    }

    @PostMapping("/{id}/close-shortage")
    @PreAuthorize("hasAnyAuthority('transfer:close', 'TENANT_ADMIN')")
    public TransferResponse closeShortage(@PathVariable Long id,
                                          @RequestHeader("Idempotency-Key") String key,
                                          @Valid @RequestBody CloseTransferShortageRequest request,
                                          Authentication authentication) {
        return service.closeShortage(id, request, key, actorId(authentication), actorName(authentication));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAnyAuthority('transfer:create', 'TENANT_ADMIN')")
    public TransferResponse voidOrder(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
                                      @Valid @RequestBody TransferReasonRequest request,
                                      Authentication authentication) {
        return service.voidOrder(id, request, key, actorId(authentication), actorName(authentication));
    }

    @GetMapping("/policy")
    @PreAuthorize("hasAnyAuthority('transfer:view', 'transfer:create', 'transfer:approve', 'transfer:close', 'transfer:policy', 'TENANT_ADMIN')")
    public TransferPolicyResponse policy() { return policyService.get(); }

    @PutMapping("/policy")
    @PreAuthorize("hasAnyAuthority('transfer:policy', 'TENANT_ADMIN')")
    public TransferPolicyResponse updatePolicy(@RequestHeader("Idempotency-Key") String key,
                                               @Valid @RequestBody UpdateTransferPolicyRequest request,
                                               Authentication authentication) {
        Long actorId = actorId(authentication);
        String actorName = actorName(authentication);
        return commandService.execute(key, "TRANSFER_POLICY_UPDATE", request, actorId, actorName,
            TransferPolicyResponse.class, () -> policyService.update(request, actorId, actorName));
    }

    private Long actorId(Authentication authentication) {
        return AuthUserResolver.resolveUserId(authentication);
    }

    private String actorName(Authentication authentication) {
        return AuthUserResolver.resolveUsername(authentication);
    }
}

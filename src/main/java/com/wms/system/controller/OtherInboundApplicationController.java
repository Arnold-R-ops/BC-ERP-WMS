package com.wms.system.controller;

import com.wms.system.dto.inbound.OtherInboundActionRequest;
import com.wms.system.dto.inbound.OtherInboundApplicationCreateRequest;
import com.wms.system.dto.inbound.OtherInboundApplicationResponse;
import com.wms.system.dto.inbound.OtherInboundApplicationUpdateRequest;
import com.wms.system.entity.enums.OtherInboundStatus;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.OtherInboundApplicationService;
import com.wms.system.service.WarehouseScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/other-inbound-applications")
@RequiredArgsConstructor
public class OtherInboundApplicationController {

    private final OtherInboundApplicationService applicationService;
    private final WarehouseScopeService warehouseScopeService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('other_inbound:manage', 'TENANT_ADMIN')")
    public ResponseEntity<List<OtherInboundApplicationResponse>> list(
        @RequestParam(required = false) OtherInboundStatus status,
        @PageableDefault(size = 20, page = 0) Pageable pageable,
        Authentication authentication
    ) {
        return ResponseEntity.ok(warehouseScopeService.filterAccessible(
            authentication,
            applicationService.list(status, pageable),
            response -> response.getWarehouseId() == null
                ? List.of() : List.of(response.getWarehouseId())
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('other_inbound:manage', 'TENANT_ADMIN')")
    public ResponseEntity<OtherInboundApplicationResponse> get(
        @PathVariable Long id,
        Authentication authentication
    ) {
        requireAccess(authentication, id);
        return ResponseEntity.ok(applicationService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('other_inbound:manage', 'TENANT_ADMIN')")
    public ResponseEntity<OtherInboundApplicationResponse> create(
        @Valid @RequestBody OtherInboundApplicationCreateRequest request,
        Authentication authentication
    ) {
        warehouseScopeService.requireAccess(
            authentication, List.of(request.getWarehouseId()),
            "OtherInboundApplication", "new");
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.create(
            request,
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('other_inbound:manage', 'TENANT_ADMIN')")
    public ResponseEntity<OtherInboundApplicationResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody OtherInboundApplicationUpdateRequest request,
        Authentication authentication
    ) {
        requireAccess(authentication, id);
        warehouseScopeService.requireAccess(
            authentication, List.of(request.getWarehouseId()),
            "OtherInboundApplication", id);
        return ResponseEntity.ok(applicationService.update(id, request));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyAuthority('other_inbound:manage', 'TENANT_ADMIN')")
    public ResponseEntity<OtherInboundApplicationResponse> confirm(
        @PathVariable Long id,
        @Valid @RequestBody OtherInboundActionRequest request,
        Authentication authentication
    ) {
        requireAccess(authentication, id);
        return ResponseEntity.ok(applicationService.confirm(
            id, request.getVersion(), AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('other_inbound:manage', 'TENANT_ADMIN')")
    public ResponseEntity<OtherInboundApplicationResponse> cancel(
        @PathVariable Long id,
        @Valid @RequestBody OtherInboundActionRequest request,
        Authentication authentication
    ) {
        requireAccess(authentication, id);
        return ResponseEntity.ok(applicationService.cancel(
            id, request.getVersion(), request.getReason(),
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAnyAuthority('other_inbound:manage', 'TENANT_ADMIN')")
    public ResponseEntity<OtherInboundApplicationResponse> voidApplication(
        @PathVariable Long id,
        @Valid @RequestBody OtherInboundActionRequest request,
        Authentication authentication
    ) {
        requireAccess(authentication, id);
        return ResponseEntity.ok(applicationService.voidApplication(
            id, request.getVersion(), request.getReason(),
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyAuthority('other_inbound:manage', 'TENANT_ADMIN')")
    public ResponseEntity<OtherInboundApplicationResponse> close(
        @PathVariable Long id,
        @Valid @RequestBody OtherInboundActionRequest request,
        Authentication authentication
    ) {
        requireAccess(authentication, id);
        return ResponseEntity.ok(applicationService.closeRemainder(
            id, request.getVersion(), request.getReason(),
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    private void requireAccess(Authentication authentication, Long id) {
        Long warehouseId = applicationService.getWarehouseId(id);
        warehouseScopeService.requireAccess(
            authentication,
            warehouseId == null ? List.of() : List.of(warehouseId),
            "OtherInboundApplication",
            id
        );
    }
}

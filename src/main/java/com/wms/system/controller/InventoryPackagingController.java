package com.wms.system.controller;

import com.wms.system.dto.inventory.*;
import com.wms.system.service.inventory.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/inventory/packaging")
@RequiredArgsConstructor
public class InventoryPackagingController {
    private final InventoryPackagingService service;
    private final InventoryPackagingCommandService commands;

    @GetMapping("/{batchId}")
    @PreAuthorize("hasAnyAuthority('inventory:view', 'stocktake:count', 'outbound:pick', 'outbound:view', 'transfer:dispatch', 'transfer:receive', 'TENANT_ADMIN')")
    public PackagingView get(@PathVariable Long batchId, Authentication auth) { return service.get(batchId, auth); }

    @PostMapping("/arrange")
    @PreAuthorize("hasAnyAuthority('stocktake:count', 'TENANT_ADMIN')")
    public List<PackagingView> arrange(@Valid @RequestBody ArrangePackagingRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication auth) {
        return commands.arrange(request, auth, key);
    }
}

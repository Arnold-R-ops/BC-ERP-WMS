package com.wms.system.controller;

import com.wms.system.dto.v45.AtpSupplyResponse;
import com.wms.system.service.AtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/atp")
@RequiredArgsConstructor
public class AtpController {

    private final AtpService atpService;

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyAuthority('inventory:view', 'SUPER_ADMIN')")
    public ResponseEntity<List<AtpSupplyResponse>> listSupply(@PathVariable("productId") Long productId) {
        return ResponseEntity.ok(atpService.listSupply(productId));
    }
}

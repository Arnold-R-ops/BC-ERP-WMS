package com.wms.system.controller;

import com.wms.system.dto.product.CreateProductRequest;
import com.wms.system.dto.product.ProductResponse;
import com.wms.system.dto.product.UpdateProductRequest;
import com.wms.system.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('product:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<ProductResponse>> list(
        @RequestParam(value = "enabledOnly", defaultValue = "false") boolean enabledOnly
    ) {
        return ResponseEntity.ok(productService.list(enabledOnly));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('product:view', 'TENANT_ADMIN')")
    public ResponseEntity<ProductResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(productService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('product:create', 'TENANT_ADMIN')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('product:edit', 'TENANT_ADMIN')")
    public ResponseEntity<ProductResponse> update(
        @PathVariable("id") Long id,
        @Valid @RequestBody UpdateProductRequest request
    ) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasAnyAuthority('product:status', 'TENANT_ADMIN')")
    public ResponseEntity<ProductResponse> activate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(productService.activate(id));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('product:status', 'TENANT_ADMIN')")
    public ResponseEntity<ProductResponse> deactivate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(productService.deactivate(id));
    }
}

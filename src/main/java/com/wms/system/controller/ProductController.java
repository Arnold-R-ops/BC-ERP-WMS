package com.wms.system.controller;

import com.wms.system.dto.product.CreateProductRequest;
import com.wms.system.dto.product.ProductResponse;
import com.wms.system.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('product:create', 'SUPER_ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        log.info("API调用: createProduct - barcode: {}", request.getBarcode());
        ProductResponse response = productService.createProduct(request);
        log.info("API响应: createProduct - id: {}, barcode: {}", response.getId(), response.getBarcode());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('product:view', 'SUPER_ADMIN')")
    public ResponseEntity<List<ProductResponse>> listProducts(
            @RequestParam(required = false) Boolean enabledOnly) {
        log.info("API调用: listProducts - enabledOnly: {}", enabledOnly);
        List<ProductResponse> responses = productService.listProducts(enabledOnly);
        log.info("API响应: listProducts - 商品数: {}", responses.size());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('product:view', 'SUPER_ADMIN')")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        log.info("API调用: getProduct - id: {}", id);
        ProductResponse response = productService.getProduct(id);
        log.info("API响应: getProduct - barcode: {}", response.getBarcode());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('product:edit', 'SUPER_ADMIN')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody CreateProductRequest request) {
        log.info("API调用: updateProduct - id: {}, barcode: {}", id, request.getBarcode());
        ProductResponse response = productService.updateProduct(id, request);
        log.info("API响应: updateProduct - id: {}, barcode: {}", response.getId(), response.getBarcode());
        return ResponseEntity.ok(response);
    }
}

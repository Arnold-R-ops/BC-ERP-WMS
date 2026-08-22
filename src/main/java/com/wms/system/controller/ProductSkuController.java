package com.wms.system.controller;

import com.wms.system.dto.productsku.CreateProductSkuRequest;
import com.wms.system.dto.productsku.ProductSkuResponse;
import com.wms.system.service.ProductSkuService;
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
@RequestMapping("/api/product-skus")
@RequiredArgsConstructor
public class ProductSkuController {

    private final ProductSkuService productSkuService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('product-sku:create', 'TENANT_ADMIN')")
    public ResponseEntity<ProductSkuResponse> createProductSku(@Valid @RequestBody CreateProductSkuRequest request) {
        log.info("API调用: createProductSku - barcode: {}", request.getBarcode());
        ProductSkuResponse response = productSkuService.createProductSku(request);
        log.info("API响应: createProductSku - id: {}, barcode: {}", response.getId(), response.getBarcode());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('product-sku:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<ProductSkuResponse>> listProductSkus(
            @RequestParam(required = false) Boolean enabledOnly,
            @RequestParam(required = false) Long productId) {
        log.info("API调用: listProductSkus - enabledOnly: {}, productId: {}", enabledOnly, productId);
        List<ProductSkuResponse> responses = productSkuService.listProductSkus(enabledOnly, productId);
        log.info("API响应: listProductSkus - SKU 数: {}", responses.size());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('product-sku:view', 'TENANT_ADMIN')")
    public ResponseEntity<ProductSkuResponse> getProductSku(@PathVariable Long id) {
        log.info("API调用: getProduct - id: {}", id);
        ProductSkuResponse response = productSkuService.getProductSku(id);
        log.info("API响应: getProduct - barcode: {}", response.getBarcode());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('product-sku:edit', 'TENANT_ADMIN')")
    public ResponseEntity<ProductSkuResponse> updateProductSku(
            @PathVariable Long id,
            @Valid @RequestBody CreateProductSkuRequest request) {
        log.info("API调用: updateProduct - id: {}, barcode: {}", id, request.getBarcode());
        ProductSkuResponse response = productSkuService.updateProductSku(id, request);
        log.info("API响应: updateProduct - id: {}, barcode: {}", response.getId(), response.getBarcode());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasAnyAuthority('product-sku:status', 'TENANT_ADMIN')")
    public ResponseEntity<ProductSkuResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(productSkuService.activate(id));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('product-sku:status', 'TENANT_ADMIN')")
    public ResponseEntity<ProductSkuResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(productSkuService.deactivate(id));
    }
}

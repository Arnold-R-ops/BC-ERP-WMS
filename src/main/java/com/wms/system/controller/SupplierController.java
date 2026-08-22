package com.wms.system.controller;

import com.wms.system.dto.supplier.CreateSupplierRequest;
import com.wms.system.dto.supplier.SupplierResponse;
import com.wms.system.dto.supplier.UpdateSupplierRequest;
import com.wms.system.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('supplier:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<SupplierResponse>> list(
        @RequestParam(value = "activeOnly", defaultValue = "false") boolean activeOnly
    ) {
        return ResponseEntity.ok(supplierService.list(activeOnly));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('supplier:view', 'TENANT_ADMIN')")
    public ResponseEntity<SupplierResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(supplierService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('supplier:create', 'TENANT_ADMIN')")
    public ResponseEntity<SupplierResponse> create(
        @Valid @RequestBody CreateSupplierRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplierService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('supplier:update', 'TENANT_ADMIN')")
    public ResponseEntity<SupplierResponse> update(
        @PathVariable("id") Long id,
        @Valid @RequestBody UpdateSupplierRequest request
    ) {
        return ResponseEntity.ok(supplierService.update(id, request));
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasAnyAuthority('supplier:update', 'TENANT_ADMIN')")
    public ResponseEntity<SupplierResponse> activate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(supplierService.activate(id));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('supplier:update', 'TENANT_ADMIN')")
    public ResponseEntity<SupplierResponse> deactivate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(supplierService.deactivate(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('supplier:delete', 'TENANT_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        supplierService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package com.wms.system.controller;

import com.wms.system.entity.Warehouse;
import com.wms.system.service.WarehouseService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
@Validated
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    public ResponseEntity<List<WarehouseResponse>> getAllWarehouses() {
        log.info("Getting all warehouses");
        List<Warehouse> warehouses = warehouseService.getAllWarehouses();
        return ResponseEntity.ok(warehouses.stream().map(this::toResponse).toList());
    }

    @GetMapping("/active")
    public ResponseEntity<List<WarehouseResponse>> getAllActiveWarehouses() {
        log.info("Getting all active warehouses");
        List<Warehouse> warehouses = warehouseService.getAllActiveWarehouses();
        return ResponseEntity.ok(warehouses.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarehouseResponse> getWarehouseById(@PathVariable("id") Long id) {
        log.info("Getting warehouse by id: {}", id);
        Warehouse warehouse = warehouseService.getWarehouseById(id);
        return ResponseEntity.ok(toResponse(warehouse));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<WarehouseResponse> getWarehouseByCode(@PathVariable("code") String code) {
        log.info("Getting warehouse by code: {}", code);
        Warehouse warehouse = warehouseService.getWarehouseByCode(code);
        return ResponseEntity.ok(toResponse(warehouse));
    }

    @PostMapping
    public ResponseEntity<WarehouseResponse> createWarehouse(@RequestBody @Validated CreateWarehouseRequest request) {
        log.info("Creating warehouse: code={}, name={}", request.code, request.name);
        Warehouse warehouse = warehouseService.createWarehouse(
                request.code,
                request.name,
                request.address,
                request.contact,
                request.phone
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(warehouse));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WarehouseResponse> updateWarehouse(
            @PathVariable("id") Long id,
            @RequestBody @Validated UpdateWarehouseRequest request) {
        log.info("Updating warehouse: id={}", id);
        Warehouse warehouse = warehouseService.updateWarehouse(
                id,
                request.name,
                request.address,
                request.contact
        );
        return ResponseEntity.ok(toResponse(warehouse));
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<WarehouseResponse> activateWarehouse(@PathVariable("id") Long id) {
        log.info("Activating warehouse: id={}", id);
        Warehouse warehouse = warehouseService.activateWarehouse(id);
        return ResponseEntity.ok(toResponse(warehouse));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<WarehouseResponse> deactivateWarehouse(@PathVariable("id") Long id) {
        log.info("Deactivating warehouse: id={}", id);
        Warehouse warehouse = warehouseService.deactivateWarehouse(id);
        return ResponseEntity.ok(toResponse(warehouse));
    }

    @GetMapping("/{id}/location-count")
    public ResponseEntity<Long> getLocationCount(@PathVariable("id") Long id) {
        log.info("Getting location count for warehouse: id={}", id);
        long count = warehouseService.getLocationCount(id);
        return ResponseEntity.ok(count);
    }

    private WarehouseResponse toResponse(Warehouse warehouse) {
        Long locationCount = warehouse.getId() == null ? 0L : warehouseService.getLocationCount(warehouse.getId());
        return new WarehouseResponse(
                warehouse.getCompanyId(),
                warehouse.getCreatedAt(),
                warehouse.getUpdatedAt(),
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getContact(),
                warehouse.getPhone(),
                warehouse.getIsActive(),
                locationCount
        );
    }

    public record WarehouseResponse(
            Long companyId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long id,
            String code,
            String name,
            String address,
            String contact,
            String phone,
            Boolean isActive,
            Long locationCount
    ) {}

    public record CreateWarehouseRequest(
            @NotBlank(message = "warehouse code is required")
            @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,19}$", message = "warehouse code format is invalid")
            String code,

            @NotBlank(message = "warehouse name is required")
            @Size(max = 100, message = "warehouse name must be at most 100 characters")
            String name,

            @Size(max = 255, message = "warehouse address must be at most 255 characters")
            String address,

            @Size(max = 50, message = "warehouse contact must be at most 50 characters")
            String contact,

            @Size(max = 20, message = "warehouse phone must be at most 20 characters")
            String phone
    ) {}

    public record UpdateWarehouseRequest(
            @Size(max = 100, message = "warehouse name must be at most 100 characters")
            String name,

            @Size(max = 255, message = "warehouse address must be at most 255 characters")
            String address,

            @Size(max = 50, message = "warehouse contact must be at most 50 characters")
            String contact
    ) {}
}

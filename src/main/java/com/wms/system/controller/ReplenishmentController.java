package com.wms.system.controller;

import com.wms.system.dto.ReorderSuggestion;
import com.wms.system.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
public class ReplenishmentController {
    private final StockPredictionService predictions;
    private final WarehouseStockThresholdService thresholds;
    private final com.wms.system.repository.WarehouseRepository warehouses;

    public record WarehouseOption(Long id, String name) {}
    @GetMapping("/api/inventory/replenishment/warehouses")
    @PreAuthorize("hasAnyAuthority('inventory:view', 'TENANT_ADMIN')")
    public List<WarehouseOption> warehouses() {
        return warehouses.findAllActive().stream().map(w -> new WarehouseOption(w.getId(), w.getName())).toList();
    }


    @GetMapping("/api/inventory/replenishment")
    @PreAuthorize("hasAnyAuthority('inventory:view', 'TENANT_ADMIN')")
    public List<ReorderSuggestion> list(@RequestParam(defaultValue = "30") Integer days,
        @RequestParam(required = false) Long warehouseId,
        @RequestParam(defaultValue = "false") boolean includeHealthy) {
        return predictions.getReorderSuggestions(days, warehouseId, includeHealthy);
    }

    public record ThresholdRequest(@NotNull @Positive Long warehouseId, @NotNull @Min(0) Integer minStock) {}

    @PutMapping("/api/product-skus/{id}/warehouse-minimum")
    @PreAuthorize("hasAnyAuthority('product-sku:edit', 'TENANT_ADMIN')")
    public void saveThreshold(@PathVariable Long id, @Valid @RequestBody ThresholdRequest request) {
        thresholds.save(id, request.warehouseId(), request.minStock());
    }
}

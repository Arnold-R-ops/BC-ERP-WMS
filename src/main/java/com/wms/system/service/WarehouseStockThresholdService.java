package com.wms.system.service;

import com.wms.system.entity.*;
import com.wms.system.repository.*;
import com.wms.system.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class WarehouseStockThresholdService {
    private final WarehouseStockThresholdRepository thresholds;
    private final WarehouseRepository warehouses;
    private final ProductSkuRepository products;

    @Transactional(readOnly = true)
    public Integer threshold(ProductSku product, Long warehouseId) {
        if (warehouseId == null) return product.getMinStock();
        var warehouse = warehouses.findById(warehouseId)
            .filter(w -> Boolean.TRUE.equals(w.getIsActive()))
            .orElseThrow(() -> new BusinessException(ErrorKeys.VALIDATION_FAILED, java.util.Map.of("reason", "请选择有效仓库")));
        return thresholds.findByProductSkuIdAndWarehouseId(product.getId(), warehouseId)
            .map(WarehouseStockThreshold::getMinStock)
            .orElseGet(() -> warehouses.findAllActive().size() == 1 ? product.getMinStock() : null);
    }

    @Transactional
    public void save(Long productId, Long warehouseId, Integer minimum) {
        if (minimum == null || minimum < 0) throw new BusinessException(ErrorKeys.VALIDATION_FAILED, java.util.Map.of("reason", "最低库存不能小于 0"));
        // Serialize first-time creation as well as subsequent updates for this SKU.
        var product = products.findByIdForThresholdUpdate(productId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_SKU_NOT_FOUND));
        var warehouse = warehouses.findById(warehouseId).filter(w -> Boolean.TRUE.equals(w.getIsActive()))
            .orElseThrow(() -> new BusinessException(ErrorKeys.VALIDATION_FAILED, java.util.Map.of("reason", "请选择有效仓库")));
        var rule = thresholds.findByProductSkuIdAndWarehouseId(productId, warehouseId).orElseGet(WarehouseStockThreshold::new);
        rule.setProductSku(product);
        rule.setWarehouse(warehouse);
        rule.setMinStock(minimum);
        thresholds.save(rule);
    }
}

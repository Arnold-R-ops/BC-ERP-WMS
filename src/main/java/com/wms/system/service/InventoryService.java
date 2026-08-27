package com.wms.system.service;

import com.wms.system.entity.Inventory;
import com.wms.system.repository.InventoryRepository;
import com.wms.system.repository.InventoryBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Inventory Management Service
 *
 * Read-only inventory queries retained for legacy consumers.
 * Inventory writes must enter through a formal business workflow.
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 2.0 (Error Key System + Audit Fields)
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryBatchRepository inventoryBatchRepository;

    /**
     * Query total stock for a product (sum across all locations)
     *
     * @param productSkuId ProductSku ID
     * @return Integer Total stock (returns 0 if no inventory records found)
     */
    @Transactional(readOnly = true)
    public Integer getTotalStock(Long productSkuId) {
        Integer totalStock = inventoryBatchRepository.sumQuantityByProductSku(productSkuId);
        return totalStock != null ? totalStock : 0;
    }

    @Transactional(readOnly = true)
    public Integer getReservedStock(Long productSkuId) {
        Integer reservedStock = inventoryBatchRepository.sumReservedQuantityByProductSku(productSkuId);
        return reservedStock != null ? reservedStock : 0;
    }

    @Transactional(readOnly = true)
    public Integer getAvailableStock(Long productSkuId) {
        Integer availableStock = inventoryBatchRepository.sumAvailableQuantityByProductSku(productSkuId);
        return availableStock != null ? availableStock : 0;
    }

    /**
     * Query stock distribution for a product (which locations have stock)
     *
     * @param productSkuId ProductSku ID
     * @return List<Inventory> Inventory records
     */
    @Transactional(readOnly = true)
    public List<Inventory> getStockDistribution(Long productSkuId) {
        return inventoryRepository.findByProductSku_Id(productSkuId);
    }

    /**
     * Query inventory records for a location (which products are stored)
     *
     * @param locationId Location ID
     * @return List<Inventory> Inventory records
     */
    @Transactional(readOnly = true)
    public List<Inventory> getStockByLocation(Long locationId) {
        return inventoryRepository.findByLocation_Id(locationId);
    }

    /**
     * Query low stock inventory records (for stock alerts)
     *
     * @return List<Inventory> Low stock records
     */
    @Transactional(readOnly = true)
    public List<Inventory> getLowStockInventories() {
        return inventoryRepository.findLowStockInventories();
    }
}

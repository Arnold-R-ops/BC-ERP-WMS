package com.wms.system.repository;

import com.wms.system.entity.WarehouseStockThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WarehouseStockThresholdRepository extends JpaRepository<WarehouseStockThreshold, Long> {
    Optional<WarehouseStockThreshold> findByProductSkuIdAndWarehouseId(Long productSkuId, Long warehouseId);
}

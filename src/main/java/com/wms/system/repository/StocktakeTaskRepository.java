package com.wms.system.repository;

import com.wms.system.entity.StocktakeTask;
import com.wms.system.entity.enums.StocktakeCycleType;
import com.wms.system.entity.enums.StocktakeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Stocktake Task Repository
 *
 * V3.8 Architecture: Smart Stocktake System
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.8 (Smart Stocktake System)
 */
@Repository
public interface StocktakeTaskRepository extends JpaRepository<StocktakeTask, Long> {

    /**
     * Find stocktake task by task number
     *
     * @param taskNo Task number
     * @return Optional<StocktakeTask>
     */
    Optional<StocktakeTask> findByTaskNo(String taskNo);

    /**
     * Find stocktake tasks by warehouse ID
     *
     * @param warehouseId Warehouse ID
     * @return List<StocktakeTask>
     */
    List<StocktakeTask> findByWarehouseId(Long warehouseId);

    /**
     * Find stocktake tasks by status
     *
     * @param status Stocktake status
     * @return List<StocktakeTask>
     */
    List<StocktakeTask> findByStatus(StocktakeStatus status);

    /**
     * Find stocktake tasks by cycle type
     *
     * @param cycleType Cycle type
     * @return List<StocktakeTask>
     */
    List<StocktakeTask> findByCycleType(StocktakeCycleType cycleType);
}

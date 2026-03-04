package com.wms.system.repository;

import com.wms.system.entity.StocktakeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Stocktake Item Repository
 *
 * V3.8 Architecture: Smart Stocktake System
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.8 (Smart Stocktake System)
 */
@Repository
public interface StocktakeItemRepository extends JpaRepository<StocktakeItem, Long> {

    /**
     * Find stocktake items by task ID
     *
     * @param taskId Task ID
     * @return List<StocktakeItem>
     */
    List<StocktakeItem> findByTaskId(Long taskId);

    /**
     * Find stocktake items by task ID and counted status
     *
     * @param taskId Task ID
     * @param isCounted Counted status
     * @return List<StocktakeItem>
     */
    List<StocktakeItem> findByTaskIdAndIsCounted(Long taskId, Boolean isCounted);

    /**
     * Count stocktake items by task ID and counted status
     *
     * @param taskId Task ID
     * @param isCounted Counted status
     * @return long Count
     */
    long countByTaskIdAndIsCounted(Long taskId, Boolean isCounted);

    /**
     * Count stocktake items by task ID and difference quantity not equal to zero
     *
     * @param taskId Task ID
     * @param differenceQty Difference quantity (0)
     * @return long Count
     */
    long countByTaskIdAndDifferenceQtyNot(Long taskId, Integer differenceQty);
}

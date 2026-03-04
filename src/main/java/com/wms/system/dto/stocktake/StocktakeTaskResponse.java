package com.wms.system.dto.stocktake;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Stocktake Task Response DTO
 *
 * V3.8 Architecture: Smart Stocktake System
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.8 (Smart Stocktake System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StocktakeTaskResponse {

    /**
     * Task ID
     */
    private Long id;

    /**
     * Task number
     */
    private String taskNo;

    /**
     * Warehouse ID
     */
    private Long warehouseId;

    /**
     * Warehouse name
     */
    private String warehouseName;

    /**
     * Cycle type
     */
    private String cycleType;

    /**
     * Cycle type description
     */
    private String cycleTypeDescription;

    /**
     * Task status
     */
    private String status;

    /**
     * Status description
     */
    private String statusDescription;

    /**
     * Snapshot time
     */
    private LocalDateTime snapshotTime;

    /**
     * Total items count
     */
    private Integer totalItems;

    /**
     * Counted items count
     */
    private Integer countedItems;

    /**
     * Difference items count
     */
    private Integer differenceItems;

    /**
     * Progress percentage (0-100)
     */
    private Integer progress;

    /**
     * Reviewed by user ID
     */
    private Long reviewedBy;

    /**
     * Reviewed by user name
     */
    private String reviewedByName;

    /**
     * Reviewed at timestamp
     */
    private LocalDateTime reviewedAt;

    /**
     * Review comment
     */
    private String reviewComment;

    /**
     * Created by user ID
     */
    private Long createdBy;

    /**
     * Created by user name
     */
    private String createdByName;

    /**
     * Created at timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Updated at timestamp
     */
    private LocalDateTime updatedAt;
}

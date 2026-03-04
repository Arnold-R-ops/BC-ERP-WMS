package com.wms.system.dto.stocktake;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Create Stocktake Task Request DTO
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
public class CreateStocktakeTaskRequest {

    /**
     * Warehouse ID
     */
    @NotNull(message = "仓库ID不能为空")
    private Long warehouseId;

    /**
     * Cycle type (MONTHLY, QUARTERLY, ANNUAL, ADHOC)
     */
    @NotBlank(message = "盘点周期类型不能为空")
    private String cycleType;
}

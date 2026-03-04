package com.wms.system.dto.stocktake;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Review Stocktake Request DTO
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
public class ReviewStocktakeRequest {

    /**
     * Approved flag (true = approve, false = reject)
     */
    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    /**
     * Review comment (optional)
     */
    private String comment;
}

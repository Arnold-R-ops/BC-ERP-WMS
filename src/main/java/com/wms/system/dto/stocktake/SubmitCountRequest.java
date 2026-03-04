package com.wms.system.dto.stocktake;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Submit Count Request DTO
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
public class SubmitCountRequest {

    /**
     * Counted quantity (actual count)
     */
    @NotNull(message = "实盘数量不能为空")
    @Min(value = 0, message = "实盘数量不能为负数")
    private Integer countedQty;

    /**
     * Remark (optional)
     */
    private String remark;
}

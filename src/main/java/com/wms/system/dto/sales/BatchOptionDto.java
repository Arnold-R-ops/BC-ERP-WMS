package com.wms.system.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 批次选项 DTO
 *
 * V3.7 架构：库存预检查
 *
 * 说明：
 * - 用于前端展示可用批次信息
 * - 包含新鲜度状态和包装状态
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchOptionDto {

    /**
     * 批次ID
     */
    private Long batchId;

    /**
     * 批次编码
     */
    private String batchCode;

    /**
     * 库位编码
     */
    private String locationCode;

    /**
     * 可用数量
     */
    private Integer quantity;

    /**
     * 过期日期
     */
    private LocalDate expiryDate;

    /**
     * 新鲜度状态
     *
     * 值：
     * - FRESH: 新鲜
     * - WARNING: 临期预警
     */
    private String freshnessStatus;

    /**
     * 包装状态
     *
     * 值：
     * - "📦 整箱": 整箱批次
     * - "📦 散货": 散货批次
     */
    private String packageStatus;

    /**
     * 距离过期天数
     */
    private Integer daysUntilExpiry;

    /**
     * 单价（可选）
     */
    private BigDecimal unitPrice;
}

package com.wms.system.dto.inventory;

import com.wms.system.entity.enums.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 第一层：SKU 聚合视图 DTO
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventorySummaryDto {
    private Long productId;                     // 产品 ID（用于点击跳转到第二层）
    private SkuInfoDto skuInfo;                 // SKU 基本信息
    private List<String> warehouseNames;        // 分布的仓库列表
    private String displayQuantity;             // 智能数量显示（如 "50箱 + 12袋"）
    private StockStatus stockStatus;            // 预警状态
    private LocalDate furthestExpiryDate;       // 最远有效期
}

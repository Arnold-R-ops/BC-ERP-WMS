package com.wms.system.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SKU 基本信息 DTO
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkuInfoDto {
    private String image;           // 图片 URL
    private String name;            // SKU 名称
    private String skuCode;         // SKU 编码（条形码）
    private String specs;           // 规格描述
}

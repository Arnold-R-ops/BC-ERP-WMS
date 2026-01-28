package com.wms.system.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 第二层：批次详情视图 DTO
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDetailDto {
    private String batchCode;           // 业务批次码
    private String traceCode;           // Hashids 追溯码（与 batchCode 相同）
    private String warehouseName;       // 仓库名称
    private String locationCode;        // 库位编码
    private Integer quantity;           // 数量
    private String packageStatus;       // 整箱/散货状态（图标）
    private LocalDate expiryDate;       // 有效期
}

package com.wms.system.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 第三层：库位反查视图 DTO
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationViewDto {
    private String locationCode;        // 库位编码
    private String warehouseName;       // 仓库名称
    private List<InventoryDetailDto> batches;  // 该库位的所有批次
}

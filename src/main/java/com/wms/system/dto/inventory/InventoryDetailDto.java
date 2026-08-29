package com.wms.system.dto.inventory;

import com.wms.system.entity.enums.InventoryQualityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Batch-level inventory detail view.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDetailDto {
    private Long batchId;
    private String batchCode;
    private String traceCode;
    private String warehouseName;
    private String locationCode;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private InventoryQualityStatus qualityStatus;
    private String packageStatus;
    private LocalDate expiryDate;
}

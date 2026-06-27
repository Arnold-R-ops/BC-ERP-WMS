package com.wms.system.dto.inventory;

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
    private String batchCode;
    private String traceCode;
    private String warehouseName;
    private String locationCode;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private String packageStatus;
    private LocalDate expiryDate;
}

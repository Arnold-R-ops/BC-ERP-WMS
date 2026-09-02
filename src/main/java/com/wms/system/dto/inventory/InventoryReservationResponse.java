package com.wms.system.dto.inventory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservationResponse {
    private Long id;
    private Long salesOrderId;
    private Long salesOrderItemId;
    private Long inventoryBatchId;
    private String batchCode;
    private Long productSkuId;
    private String productName;
    private Long locationId;
    private String locationCode;
    /** Internal authorization scope; never exposed in the public DTO. */
    @JsonIgnore
    private Long warehouseId;
    private Integer reservedQty;
    private Integer consumedQty;
    private Integer releasedQty;
    private Integer openQty;
    private String status;
    private String statusDescription;
    private LocalDateTime expiresAt;
    private String sourceType;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

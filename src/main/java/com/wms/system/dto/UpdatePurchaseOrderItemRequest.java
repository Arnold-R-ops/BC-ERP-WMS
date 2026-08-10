package com.wms.system.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePurchaseOrderItemRequest {

    /** Existing line ID; null means a new line. */
    private Long id;

    @NotNull(message = "ProductSku ID is required")
    private Long productSkuId;

    @NotNull(message = "Ordered quantity is required")
    @Min(value = 1, message = "Ordered quantity must be greater than 0")
    private Integer orderedQuantity;

    @DecimalMin(value = "0.00", message = "Unit cost cannot be negative")
    private BigDecimal unitCost;

    private LocalDate expiryDate;
    private LocalDate productionDate;

    @Size(max = 100, message = "External batch code cannot exceed 100 characters")
    private String externalBatchCode;

    @Size(max = 500, message = "Item remark cannot exceed 500 characters")
    private String remark;
}

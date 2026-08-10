package com.wms.system.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePurchaseOrderRequest {

    @NotNull(message = "Purchase order version is required")
    private Long version;

    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    @NotEmpty(message = "At least one purchase order item is required")
    @Valid
    private List<UpdatePurchaseOrderItemRequest> items;

    private LocalDate expectedDate;

    @Size(max = 500, message = "Remark cannot exceed 500 characters")
    private String remark;
}

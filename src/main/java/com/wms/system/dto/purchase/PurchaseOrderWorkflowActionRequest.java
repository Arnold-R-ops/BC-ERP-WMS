package com.wms.system.dto.purchase;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PurchaseOrderWorkflowActionRequest {
    @NotNull
    private Long version;

    @Size(max = 500)
    private String reason;
}

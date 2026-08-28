package com.wms.system.dto.purchase;

import com.wms.system.entity.enums.PurchaseApprovalMode;
import com.wms.system.entity.enums.ShortReceiptClosureMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PurchaseGovernancePolicyUpdateRequest {
    @NotNull
    private PurchaseApprovalMode purchaseApprovalMode;

    @NotNull
    private ShortReceiptClosureMode shortReceiptClosureMode;

    @NotBlank
    @Size(max = 500)
    private String reason;

    @AssertTrue
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "true")
    private boolean confirmed;
}

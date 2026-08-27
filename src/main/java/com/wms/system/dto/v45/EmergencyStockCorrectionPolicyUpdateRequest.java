package com.wms.system.dto.v45;

import com.wms.system.entity.enums.EmergencyCorrectionApprovalMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmergencyStockCorrectionPolicyUpdateRequest {
    @NotNull
    private EmergencyCorrectionApprovalMode mode;

    @NotBlank
    @Size(max = 500)
    private String reason;

    @AssertTrue
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "true")
    private boolean confirmed;
}

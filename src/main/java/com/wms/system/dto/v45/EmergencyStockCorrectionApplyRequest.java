package com.wms.system.dto.v45;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmergencyStockCorrectionApplyRequest {
    @AssertTrue
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "true")
    private boolean confirmed;

    @NotBlank
    @Size(max = 1000)
    private String previewToken;
}

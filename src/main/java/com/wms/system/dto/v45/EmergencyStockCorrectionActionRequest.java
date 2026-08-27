package com.wms.system.dto.v45;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmergencyStockCorrectionActionRequest {
    @NotBlank
    @Size(max = 500)
    private String comment;
}

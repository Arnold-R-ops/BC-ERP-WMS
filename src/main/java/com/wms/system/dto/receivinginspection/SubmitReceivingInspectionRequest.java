package com.wms.system.dto.receivinginspection;

import com.wms.system.entity.enums.ReceivingInspectionMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record SubmitReceivingInspectionRequest(
    @NotNull ReceivingInspectionMethod inspectionMethod,
    @Positive Integer sampleQuantity,
    @NotNull @Min(0) Integer defectQuantity,
    @NotBlank @Size(max = 80) String reasonCode,
    @Size(max = 500) String reasonDetail,
    @NotEmpty List<@Valid Line> lines
) {
    @Schema(name = "SubmitReceivingInspectionLineRequest")
    public record Line(
        @NotNull @Positive Long inspectionTaskLineId,
        @NotNull @Min(0) Integer passedQuantity,
        @NotNull @Min(0) Integer quarantinedQuantity
    ) {
    }
}

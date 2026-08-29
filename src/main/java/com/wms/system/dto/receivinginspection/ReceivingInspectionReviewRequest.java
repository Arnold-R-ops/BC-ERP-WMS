package com.wms.system.dto.receivinginspection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReceivingInspectionReviewRequest(
    @NotBlank @Size(max = 500) String comment
) {
}

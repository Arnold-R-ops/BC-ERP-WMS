package com.wms.system.dto.receivinginspection;

import com.wms.system.entity.enums.ReceivingInspectionReviewMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateReceivingInspectionPolicyRequest(
    boolean defaultInspectionRequired,
    @NotNull ReceivingInspectionReviewMode reviewMode,
    @NotBlank @Size(max = 500) String reason
) {
}

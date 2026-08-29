package com.wms.system.dto.receivinginspection;

import com.wms.system.entity.enums.ReceivingInspectionRequirement;
import com.wms.system.entity.enums.ReceivingInspectionReviewMode;

import java.util.List;

public record ReceivingInspectionPolicyResponse(
    boolean defaultInspectionRequired,
    ReceivingInspectionReviewMode reviewMode,
    List<ReceivingInspectionRequirement> skuRequirements,
    List<ReceivingInspectionReviewMode> reviewModes
) {
}

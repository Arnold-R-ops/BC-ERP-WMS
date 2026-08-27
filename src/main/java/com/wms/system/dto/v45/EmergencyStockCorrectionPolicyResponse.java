package com.wms.system.dto.v45;

import com.wms.system.entity.enums.EmergencyCorrectionApprovalMode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EmergencyStockCorrectionPolicyResponse {
    private EmergencyCorrectionApprovalMode configuredMode;
    private EmergencyCorrectionApprovalMode effectiveMode;
    private boolean approvalEntitled;
    private boolean configurable;
    private List<EmergencyCorrectionApprovalMode> availableModes;
}

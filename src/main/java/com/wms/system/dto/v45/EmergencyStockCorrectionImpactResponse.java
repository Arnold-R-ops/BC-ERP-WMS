package com.wms.system.dto.v45;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class EmergencyStockCorrectionImpactResponse {
    private Long correctionId;
    private Integer snapshotSystemQty;
    private Integer countedQty;
    private Integer adjustmentQty;
    private Integer currentQty;
    private Integer reservedQty;
    private Long projectedQty;
    private boolean canApply;
    private List<Long> affectedSalesOrderIds;
    private String previewToken;
    private Instant previewExpiresAt;
}

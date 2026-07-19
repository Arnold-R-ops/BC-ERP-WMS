package com.wms.system.dto.v45;

import com.wms.system.entity.enums.EmergencyCorrectionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class EmergencyStockCorrectionResponse {
    private Long id;
    private String correctionNo;
    private Long productSkuId;
    private Long locationId;
    private Long inventoryBatchId;
    private String batchCode;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private Integer systemQty;
    private Integer countedQty;
    private Integer adjustmentQty;
    private String reasonCode;
    private String reasonDetail;
    private String evidenceUrl;
    private Long relatedSalesOrderId;
    private EmergencyCorrectionStatus status;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private Long appliedBy;
    private LocalDateTime appliedAt;
    private String reviewComment;
    private String approvalComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

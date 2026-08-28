package com.wms.system.dto.inbound;

import com.wms.system.entity.enums.OtherInboundBusinessType;
import com.wms.system.entity.enums.OtherInboundStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OtherInboundApplicationResponse {
    private Long id;
    private Long version;
    private String applicationNumber;
    private OtherInboundBusinessType businessType;
    private String sourceReference;
    private String businessReason;
    private Long warehouseId;
    private String warehouseName;
    private Long legacyInboundOrderId;
    private OtherInboundStatus status;
    private Integer plannedQuantity;
    private Integer receivedQuantity;
    private String createdByName;
    private LocalDateTime confirmedAt;
    private String terminationReason;
    private String remark;
    private List<OtherInboundApplicationItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

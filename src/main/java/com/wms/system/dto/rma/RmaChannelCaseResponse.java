package com.wms.system.dto.rma;

import com.wms.system.entity.enums.RmaChannelCaseStatus;

import java.time.LocalDateTime;

public record RmaChannelCaseResponse(
    Long id,
    String channel,
    String storeIdentifier,
    String externalCaseId,
    String externalOrderId,
    Long salesOrderId,
    String salesOrderNumber,
    RmaChannelCaseStatus status,
    Long rmaId,
    LocalDateTime receivedAt,
    LocalDateTime matchedAt
) { }

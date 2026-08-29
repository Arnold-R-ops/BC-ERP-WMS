package com.wms.system.dto.receivinginspection;

import com.wms.system.entity.enums.*;

import java.time.LocalDateTime;

public record ReceivingArrivalExceptionResponse(
    Long id,
    String exceptionNumber,
    Long receivingTaskId,
    Long productSkuId,
    String skuCode,
    ReceivingArrivalExceptionType exceptionType,
    ReceivingArrivalDisposition disposition,
    Integer observedQuantity,
    String reasonDetail,
    String recordedByName,
    LocalDateTime recordedAt
) {
}

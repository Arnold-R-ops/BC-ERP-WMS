package com.wms.system.dto.receivinginspection;

import com.wms.system.entity.enums.*;
import jakarta.validation.constraints.*;

public record RecordReceivingArrivalExceptionRequest(
    @Positive Long productSkuId,
    @NotNull ReceivingArrivalExceptionType exceptionType,
    @NotNull ReceivingArrivalDisposition disposition,
    @NotNull @Positive Integer observedQuantity,
    @Size(max = 500) String reasonDetail
) {
}

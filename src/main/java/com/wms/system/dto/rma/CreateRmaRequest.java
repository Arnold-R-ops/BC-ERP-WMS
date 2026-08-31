package com.wms.system.dto.rma;

import com.wms.system.entity.enums.RmaStatutoryBasis;
import com.wms.system.entity.enums.RmaType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public record CreateRmaRequest(
    @NotNull Long salesOrderId,
    Long shipmentId,
    @NotNull Long warehouseId,
    @NotNull RmaType rmaType,
    @NotNull RmaStatutoryBasis statutoryBasis,
    @NotBlank @Size(max = 80) String reasonCode,
    @Size(max = 1000) String reasonDetail,
    LocalDateTime customerShippedAt,
    @Size(max = 80) String returnCarrier,
    @Size(max = 120) String returnTrackingNo,
    Long channelCaseId,
    @NotEmpty List<@Valid Line> lines
) {
    public record Line(@NotNull Long outboundTaskId, @NotNull @Min(1) Integer quantity) { }
}

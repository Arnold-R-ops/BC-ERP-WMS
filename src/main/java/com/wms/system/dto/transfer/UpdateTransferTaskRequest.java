package com.wms.system.dto.transfer;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record UpdateTransferTaskRequest(
    @NotNull @PositiveOrZero Long version,
    @Min(1) @Max(100) Integer priority,
    LocalDateTime expectedFrom,
    LocalDateTime expectedTo,
    @Size(max = 100) String assignee,
    @Size(max = 100) String teamName,
    @Size(max = 100) String operationArea,
    @Size(max = 500) String warehouseRemark
) { }

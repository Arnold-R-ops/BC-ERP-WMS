package com.wms.system.dto.rma;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record UpdateRmaTaskRequest(
    @Min(1) @Max(100) Integer priority,
    LocalDateTime expectedFrom,
    LocalDateTime expectedTo,
    @Size(max = 100) String assignee,
    @Size(max = 100) String teamName,
    @Size(max = 100) String operationArea,
    @Size(max = 500) String warehouseRemark
) { }

package com.wms.system.dto.receiving;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record UpdateReceivingPlanOperationsRequest(
    @NotNull Long version,
    @Min(0) Integer priority,
    LocalDateTime expectedWindowStart,
    LocalDateTime expectedWindowEnd,
    Long assignedReceiverId,
    @Size(max = 100) String assignedReceiverName,
    @Size(max = 100) String assignedTeam,
    @Size(max = 100) String dockArea,
    @Size(max = 100) String workArea,
    @Size(max = 500) String warehouseNotes
) {
}

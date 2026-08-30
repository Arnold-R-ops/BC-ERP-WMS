package com.wms.system.dto.inventorydisposition;

import java.time.LocalDateTime;
import java.util.List;

public record InventoryDispositionExecutionResponse(
    Long id,
    String executionNumber,
    Long executedBy,
    String executedByName,
    LocalDateTime executedAt,
    String executionNote,
    List<InventoryDispositionExecutionLineResponse> lines
) { }

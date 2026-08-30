package com.wms.system.dto.inventorydisposition;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ExecuteInventoryDispositionRequest(
    @NotEmpty List<@Valid InventoryDispositionExecutionQuantityRequest> lines,
    @Size(max = 500) String executionNote
) { }

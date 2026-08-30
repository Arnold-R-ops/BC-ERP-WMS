package com.wms.system.dto.inventorydisposition;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateInventoryScrapOrderRequest(
    @NotEmpty List<@Valid InventoryDispositionQuantityRequest> lines,
    @NotBlank @Size(max = 80) String reasonCode,
    @Size(max = 500) String reasonDetail,
    @Size(max = 120) String externalReference,
    @Size(max = 120) String recallNoticeNumber,
    boolean saveAsDraft
) { }

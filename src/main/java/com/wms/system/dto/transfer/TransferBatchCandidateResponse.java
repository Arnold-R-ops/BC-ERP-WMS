package com.wms.system.dto.transfer;

import java.time.LocalDate;

public record TransferBatchCandidateResponse(
    Long id,
    String batchCode,
    String externalBatchCode,
    Long locationId,
    String locationCode,
    int availableQuantity,
    LocalDate productionDate,
    LocalDate expiryDate
) { }

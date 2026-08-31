package com.wms.system.dto.rma;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RmaDecisionRequest(@NotBlank @Size(max = 500) String comment) { }

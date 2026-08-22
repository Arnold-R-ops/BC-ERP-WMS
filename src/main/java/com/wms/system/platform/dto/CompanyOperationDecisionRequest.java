package com.wms.system.platform.dto;

import jakarta.validation.constraints.NotBlank;

public record CompanyOperationDecisionRequest(@NotBlank String decision) {}

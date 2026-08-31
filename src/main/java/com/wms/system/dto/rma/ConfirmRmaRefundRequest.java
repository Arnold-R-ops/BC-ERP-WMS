package com.wms.system.dto.rma;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ConfirmRmaRefundRequest(
    @NotBlank @Size(max = 120) String reference,
    @NotNull @DecimalMin("0.00") BigDecimal amount,
    @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
    boolean succeeded,
    @Size(max = 500) String failureReason
) { }

package com.wms.system.dto.rma;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record RequestRmaResolutionRequest(
    @NotNull ResolutionType resolutionType,
    @DecimalMin("0.00") BigDecimal refundAmount,
    @Pattern(regexp = "^[A-Z]{3}$") String currency,
    boolean advance,
    @Size(max = 500) String reason,
    List<@Valid ExchangeLine> exchangeLines
) {
    public enum ResolutionType { REFUND, EXCHANGE }
    public record ExchangeLine(@NotNull Long rmaLineId, @NotNull @Min(1) Integer quantity) { }
}

package com.wms.system.dto.inventory;

import com.wms.system.entity.StockPackaging;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ArrangePackagingRequest(
    @NotNull Mode mode,
    @NotEmpty @Size(max = 100) List<@Valid Source> sources,
    @NotEmpty @Size(max = 100) List<@Valid Result> results
) {
    public enum Mode { VERIFY, ARRANGE }
    public record Source(@NotNull @Positive Long batchId, @NotNull @Min(0) Integer version) {}
    public record Result(@NotNull @Positive Long locationId, @NotNull @Valid StockPackaging packaging) {}
}

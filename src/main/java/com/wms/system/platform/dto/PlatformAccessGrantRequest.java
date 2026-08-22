package com.wms.system.platform.dto;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
public record PlatformAccessGrantRequest(
    @NotNull Long platformUserId, @NotEmpty List<@Pattern(regexp="READ|EXPORT") String> capabilities,
    @NotEmpty List<@Positive Long> tenantIds, @NotEmpty List<@Pattern(regexp="users|roles|warehouses|products|inventory|sales_orders") String> datasets,
    OffsetDateTime effectiveFrom, OffsetDateTime expiresAt) { }

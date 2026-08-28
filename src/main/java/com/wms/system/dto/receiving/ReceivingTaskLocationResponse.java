package com.wms.system.dto.receiving;

/** Minimal warehouse-safe location option exposed only in a receiving-task scope. */
public record ReceivingTaskLocationResponse(
    Long id,
    String code,
    String name
) {
}

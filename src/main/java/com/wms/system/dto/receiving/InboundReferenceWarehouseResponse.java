package com.wms.system.dto.receiving;

/** Minimal active-warehouse choice shared by inbound document forms. */
public record InboundReferenceWarehouseResponse(
    Long id,
    String code,
    String name
) {
}

package com.wms.system.dto.receiving;

/** Minimal operational SKU choice shared by purchase and other-inbound forms. */
public record InboundReferenceSkuResponse(
    Long id,
    String skuCode,
    String name,
    String barcode,
    Boolean expiryManagementEnabled
) {
}

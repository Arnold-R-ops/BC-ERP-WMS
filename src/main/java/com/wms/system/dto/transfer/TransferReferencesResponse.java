package com.wms.system.dto.transfer;

import java.time.LocalDate;
import java.util.List;

public record TransferReferencesResponse(
    List<WarehouseRef> warehouses,
    List<SkuRef> skus,
    List<BackorderRef> backorders
) {
    public record WarehouseRef(Long id, String code, String name) { }
    public record SkuRef(Long id, String skuCode, String name, String barcode) { }
    public record BackorderRef(Long id, Long salesOrderId, Long productSkuId,
                               int remainingQuantity, LocalDate promisedDate) { }
}

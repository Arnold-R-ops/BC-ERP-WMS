package com.wms.system.service;

import com.wms.system.dto.receiving.InboundReferenceSkuResponse;
import com.wms.system.dto.receiving.InboundReferenceWarehouseResponse;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InboundReferenceService {

    private final ProductSkuRepository productSkuRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public List<InboundReferenceSkuResponse> listOperationalSkus() {
        return productSkuRepository.findAllOperationalOrderBySkuCodeAsc().stream()
            .map(sku -> new InboundReferenceSkuResponse(
                sku.getId(), sku.getSkuCode(), sku.getName(), sku.getBarcode(),
                sku.getExpiryManagementEnabled()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<InboundReferenceWarehouseResponse> listActiveWarehouses() {
        return warehouseRepository.findAllActive().stream()
            .map(warehouse -> new InboundReferenceWarehouseResponse(
                warehouse.getId(), warehouse.getCode(), warehouse.getName()))
            .toList();
    }
}

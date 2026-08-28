package com.wms.system.service;

import com.wms.system.dto.v45.AtpSupplyResponse;
import com.wms.system.entity.PurchaseOrder;
import com.wms.system.entity.PurchaseOrderItem;
import com.wms.system.repository.PurchaseOrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AtpService {

    private final PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Transactional(readOnly = true)
    public List<AtpSupplyResponse> listSupply(Long productSkuId) {
        return purchaseOrderItemRepository.findAtpSupplyByProductSku(productSkuId).stream()
            .map(this::toResponse)
            .toList();
    }

    private AtpSupplyResponse toResponse(PurchaseOrderItemRepository.AtpSupplyRow row) {
        PurchaseOrderItem item = row.getPurchaseOrderItem();
        PurchaseOrder order = item.getPurchaseOrder();
        int confirmedInbound = row.getConfirmedInboundQuantity().intValue();
        int committed = item.getCommittedQty() == null ? 0 : item.getCommittedQty();
        return AtpSupplyResponse.builder()
            .purchaseOrderId(order.getId())
            .poNumber(order.getPoNumber())
            .purchaseOrderItemId(item.getId())
            .productSkuId(item.getProductSku().getId())
            .productName(item.getProductSku().getName())
            .expectedDate(order.getExpectedDate())
            .orderedQty(item.getOrderedQuantity())
            .receivedQty(item.getReceivedQuantity())
            .confirmedInboundQty(confirmedInbound)
            .committedQty(committed)
            .availableToPromiseQty(Math.max(0, confirmedInbound - committed))
            .supplierReliabilityScore(order.getSupplierReliabilityScore())
            .build();
    }
}

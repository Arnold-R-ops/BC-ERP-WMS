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
    public List<AtpSupplyResponse> listSupply(Long productId) {
        return purchaseOrderItemRepository.findAtpSupplyByProduct(productId).stream()
            .map(this::toResponse)
            .toList();
    }

    private AtpSupplyResponse toResponse(PurchaseOrderItem item) {
        PurchaseOrder order = item.getPurchaseOrder();
        return AtpSupplyResponse.builder()
            .purchaseOrderId(order.getId())
            .poNumber(order.getPoNumber())
            .purchaseOrderItemId(item.getId())
            .productId(item.getProduct().getId())
            .productName(item.getProduct().getName())
            .expectedDate(order.getExpectedDate())
            .orderedQty(item.getOrderedQuantity())
            .receivedQty(item.getReceivedQuantity())
            .committedQty(item.getCommittedQty())
            .availableToPromiseQty(item.getAvailableToPromiseQuantity())
            .supplierReliabilityScore(order.getSupplierReliabilityScore())
            .build();
    }
}

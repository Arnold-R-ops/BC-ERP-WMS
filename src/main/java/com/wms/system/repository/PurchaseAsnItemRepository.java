package com.wms.system.repository;

import com.wms.system.entity.PurchaseAsnItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseAsnItemRepository extends JpaRepository<PurchaseAsnItem, Long> {
    List<PurchaseAsnItem> findByPurchaseAsnIdOrderByIdAsc(Long purchaseAsnId);
}

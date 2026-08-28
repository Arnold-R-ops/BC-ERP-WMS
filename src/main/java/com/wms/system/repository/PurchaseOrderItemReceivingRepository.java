package com.wms.system.repository;

import com.wms.system.entity.PurchaseOrderItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PurchaseOrderItemReceivingRepository extends JpaRepository<PurchaseOrderItem, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PurchaseOrderItem i where i.id in :ids order by i.id")
    List<PurchaseOrderItem> findAllByIdForReceiving(@Param("ids") Collection<Long> ids);
}

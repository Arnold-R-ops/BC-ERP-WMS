package com.wms.system.repository;

import com.wms.system.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Locking repository kept separate from the legacy purchase repository. */
public interface PurchaseOrderReceivingRepository extends JpaRepository<PurchaseOrder, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrder p where p.id = :id")
    Optional<PurchaseOrder> findByIdForReceiving(@Param("id") Long id);
}

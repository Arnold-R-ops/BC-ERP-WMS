package com.wms.system.repository;

import com.wms.system.entity.PurchaseAsn;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseAsnRepository extends JpaRepository<PurchaseAsn, Long> {

    List<PurchaseAsn> findByPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PurchaseAsn a where a.id = :id")
    Optional<PurchaseAsn> findByIdForUpdate(@Param("id") Long id);
}

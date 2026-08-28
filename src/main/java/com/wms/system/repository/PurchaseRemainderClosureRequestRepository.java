package com.wms.system.repository;

import com.wms.system.entity.PurchaseRemainderClosureRequest;
import com.wms.system.entity.enums.PurchaseRemainderClosureStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseRemainderClosureRequestRepository
    extends JpaRepository<PurchaseRemainderClosureRequest, Long> {

    List<PurchaseRemainderClosureRequest> findByStatusOrderByIdDesc(
        PurchaseRemainderClosureStatus status);

    List<PurchaseRemainderClosureRequest> findAllByOrderByIdDesc();

    Optional<PurchaseRemainderClosureRequest> findFirstByPurchaseAsnIdAndStatus(
        Long purchaseAsnId,
        PurchaseRemainderClosureStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from PurchaseRemainderClosureRequest request where request.id = :id")
    Optional<PurchaseRemainderClosureRequest> findByIdForUpdate(@Param("id") Long id);
}

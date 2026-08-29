package com.wms.system.repository;

import com.wms.system.entity.InventoryQualityAction;
import com.wms.system.entity.enums.InventoryQualityActionStatus;
import com.wms.system.entity.enums.InventoryQualityActionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryQualityActionRepository extends JpaRepository<InventoryQualityAction, Long> {
    Optional<InventoryQualityAction> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM InventoryQualityAction a WHERE a.id = :id")
    Optional<InventoryQualityAction> findByIdForUpdate(@Param("id") Long id);

    List<InventoryQualityAction> findAllByOrderByIdDesc();
    List<InventoryQualityAction> findByActionTypeOrderByIdDesc(InventoryQualityActionType actionType);
    List<InventoryQualityAction> findByStatusOrderByIdDesc(InventoryQualityActionStatus status);

    @Query("""
        SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
        FROM InventoryQualityAction a JOIN a.lines l
        WHERE a.actionType = com.wms.system.entity.enums.InventoryQualityActionType.RELEASE
          AND a.status = com.wms.system.entity.enums.InventoryQualityActionStatus.PENDING_REVIEW
          AND l.sourceBatchId = :batchId
        """)
    boolean existsPendingReleaseForBatch(@Param("batchId") Long batchId);
}

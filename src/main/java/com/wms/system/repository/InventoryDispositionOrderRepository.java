package com.wms.system.repository;

import com.wms.system.entity.InventoryDispositionOrder;
import com.wms.system.entity.enums.InventoryDispositionStatus;
import com.wms.system.entity.enums.InventoryDispositionType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface InventoryDispositionOrderRepository extends JpaRepository<InventoryDispositionOrder, Long> {
    List<InventoryDispositionOrder> findAllByOrderByIdDesc();

    List<InventoryDispositionOrder> findByStatusOrderByIdDesc(InventoryDispositionStatus status);

    List<InventoryDispositionOrder> findByDispositionTypeOrderByIdDesc(InventoryDispositionType type);

    Optional<InventoryDispositionOrder> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from InventoryDispositionOrder o where o.id = :id")
    Optional<InventoryDispositionOrder> findByIdForUpdate(@Param("id") Long id);
}

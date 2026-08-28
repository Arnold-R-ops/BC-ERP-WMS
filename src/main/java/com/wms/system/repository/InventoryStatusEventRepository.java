package com.wms.system.repository;

import com.wms.system.entity.InventoryStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryStatusEventRepository extends JpaRepository<InventoryStatusEvent, Long> {
    Optional<InventoryStatusEvent> findByIdempotencyKey(String idempotencyKey);

    List<InventoryStatusEvent> findByInventoryBatchIdOrderByCreatedAtAsc(Long inventoryBatchId);
}

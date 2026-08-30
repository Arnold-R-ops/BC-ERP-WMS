package com.wms.system.repository;

import com.wms.system.entity.InventoryDispositionExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryDispositionExecutionRepository extends JpaRepository<InventoryDispositionExecution, Long> {
    Optional<InventoryDispositionExecution> findByIdempotencyKey(String idempotencyKey);
}

package com.wms.system.repository;

import com.wms.system.entity.ReceiptEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiptEventRepository extends JpaRepository<ReceiptEvent, Long> {
    Optional<ReceiptEvent> findByIdempotencyKey(String idempotencyKey);
    List<ReceiptEvent> findByReceivingTaskIdOrderByIdAsc(Long receivingTaskId);
}

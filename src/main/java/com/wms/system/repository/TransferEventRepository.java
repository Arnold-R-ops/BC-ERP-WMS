package com.wms.system.repository;

import com.wms.system.entity.TransferEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferEventRepository extends JpaRepository<TransferEvent, Long> {
    List<TransferEvent> findByTransferOrderIdOrderById(Long transferOrderId);
}

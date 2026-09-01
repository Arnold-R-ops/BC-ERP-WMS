package com.wms.system.repository;

import com.wms.system.entity.TransferReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransferReceiptRepository extends JpaRepository<TransferReceipt, Long> {
    List<TransferReceipt> findByTransferOrderIdOrderById(Long transferOrderId);
    Optional<TransferReceipt> findByIdempotencyKey(String idempotencyKey);
}

package com.wms.system.repository;

import com.wms.system.entity.TransferDispatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransferDispatchRepository extends JpaRepository<TransferDispatch, Long> {
    List<TransferDispatch> findByTransferOrderIdOrderById(Long transferOrderId);
    Optional<TransferDispatch> findByIdempotencyKey(String idempotencyKey);
}

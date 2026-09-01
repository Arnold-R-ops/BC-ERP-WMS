package com.wms.system.repository;

import com.wms.system.entity.TransferReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferReceiptLineRepository extends JpaRepository<TransferReceiptLine, Long> {
    List<TransferReceiptLine> findByReceiptTransferOrderIdOrderById(Long transferOrderId);
}

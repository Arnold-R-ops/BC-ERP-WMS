package com.wms.system.repository;

import com.wms.system.entity.SalesReturnReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesReturnReceiptRepository extends JpaRepository<SalesReturnReceipt, Long> {
    List<SalesReturnReceipt> findByRmaIdOrderById(Long rmaId);
}

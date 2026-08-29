package com.wms.system.repository;

import com.wms.system.entity.ReceivingArrivalException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface ReceivingArrivalExceptionRepository
    extends JpaRepository<ReceivingArrivalException, Long> {
    Optional<ReceivingArrivalException> findByIdempotencyKey(String idempotencyKey);
    List<ReceivingArrivalException> findByReceivingTaskIdOrderByIdDesc(Long receivingTaskId);
}

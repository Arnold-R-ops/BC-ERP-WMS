package com.wms.system.repository;

import com.wms.system.entity.SalesReturnRma;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SalesReturnRmaRepository extends JpaRepository<SalesReturnRma, Long> {
    List<SalesReturnRma> findAllByOrderByIdDesc();
    List<SalesReturnRma> findBySalesOrderIdOrderByIdDesc(Long salesOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SalesReturnRma r where r.id = :id")
    Optional<SalesReturnRma> findByIdForUpdate(@Param("id") Long id);

    Optional<SalesReturnRma> findByChannelAndExternalCaseId(String channel, String externalCaseId);
}

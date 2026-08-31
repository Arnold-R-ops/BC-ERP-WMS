package com.wms.system.repository;

import com.wms.system.entity.SalesReturnReceiptLine;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SalesReturnReceiptLineRepository extends JpaRepository<SalesReturnReceiptLine, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from SalesReturnReceiptLine l where l.id in :ids order by l.id")
    List<SalesReturnReceiptLine> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Query("select l from SalesReturnReceiptLine l where l.receipt.rma.id = :rmaId order by l.id")
    List<SalesReturnReceiptLine> findByRmaId(@Param("rmaId") Long rmaId);
}

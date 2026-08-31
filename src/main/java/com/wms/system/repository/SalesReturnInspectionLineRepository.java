package com.wms.system.repository;

import com.wms.system.entity.SalesReturnInspectionLine;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SalesReturnInspectionLineRepository extends JpaRepository<SalesReturnInspectionLine, Long> {
    @Query("select coalesce(sum(l.quantity), 0) from SalesReturnInspectionLine l where l.receiptLine.id = :receiptLineId")
    Integer sumInspectedByReceiptLineId(@Param("receiptLineId") Long receiptLineId);
}

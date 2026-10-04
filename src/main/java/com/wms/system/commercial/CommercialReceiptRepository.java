package com.wms.system.commercial;
import org.springframework.data.jpa.repository.*;
import java.math.BigDecimal;
import java.util.*;
public interface CommercialReceiptRepository extends JpaRepository<CommercialReceipt,Long> {
    List<CommercialReceipt> findByDocumentIdOrderById(Long documentId);
    Optional<CommercialReceipt> findByRequestKey(String requestKey);
    Optional<CommercialReceipt> findByReversesId(Long reversesId);
    @Query("select coalesce(sum(case when r.kind='RECEIPT' then r.amount else -r.amount end),0) from CommercialReceipt r where r.documentId=:documentId")
    BigDecimal paid(Long documentId);
}

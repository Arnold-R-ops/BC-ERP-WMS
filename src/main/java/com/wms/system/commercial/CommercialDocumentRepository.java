package com.wms.system.commercial;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface CommercialDocumentRepository extends JpaRepository<CommercialDocument,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from CommercialDocument d where d.id=:id")
    Optional<CommercialDocument> lock(Long id);
    Optional<CommercialDocument> findByKindAndRequestKey(String kind,String requestKey);
    Optional<CommercialDocument> findByKindAndSalesOrderIdAndStatus(String kind,Long salesOrderId,String status);
    @Query("select d from CommercialDocument d, Customer c where d.customerId=c.id and d.kind=:kind and (:ownerId is null or c.ownerId=:ownerId) and (:orderId is null or d.salesOrderId=:orderId) order by d.id desc")
    Page<CommercialDocument> search(String kind,Long ownerId,Long orderId,Pageable pageable);
}

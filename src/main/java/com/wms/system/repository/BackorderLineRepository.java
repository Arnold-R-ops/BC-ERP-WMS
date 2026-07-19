package com.wms.system.repository;

import com.wms.system.entity.BackorderLine;
import com.wms.system.entity.enums.BackorderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BackorderLineRepository extends JpaRepository<BackorderLine, Long> {

    List<BackorderLine> findBySalesOrderId(Long salesOrderId);

    List<BackorderLine> findBySalesOrderItemId(Long salesOrderItemId);

    @Query("""
        SELECT b
        FROM BackorderLine b
        WHERE b.productSkuId = :productSkuId
          AND b.status IN :statuses
        ORDER BY b.priority ASC, b.createdAt ASC, b.id ASC
        """)
    List<BackorderLine> findOpenByProductSkuForWakeup(
        @Param("productSkuId") Long productSkuId,
        @Param("statuses") Collection<BackorderStatus> statuses
    );
}

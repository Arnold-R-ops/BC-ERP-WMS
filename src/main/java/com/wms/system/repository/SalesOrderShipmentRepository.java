package com.wms.system.repository;

import com.wms.system.entity.SalesOrderShipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalesOrderShipmentRepository extends JpaRepository<SalesOrderShipment, Long> {

    List<SalesOrderShipment> findBySalesOrderIdOrderByIdAsc(Long salesOrderId);

    Optional<SalesOrderShipment> findByIdAndSalesOrderId(Long id, Long salesOrderId);

    Optional<SalesOrderShipment> findFirstBySalesOrderIdAndTrackingNo(Long salesOrderId, String trackingNo);
}

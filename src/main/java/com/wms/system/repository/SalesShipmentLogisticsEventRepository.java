package com.wms.system.repository;

import com.wms.system.entity.SalesShipmentLogisticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesShipmentLogisticsEventRepository extends JpaRepository<SalesShipmentLogisticsEvent, Long> {
    List<SalesShipmentLogisticsEvent> findByShipmentIdOrderByIdAsc(Long shipmentId);
}

package com.wms.system.repository;

import com.wms.system.entity.SalesReturnEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesReturnEventRepository extends JpaRepository<SalesReturnEvent, Long> {
    List<SalesReturnEvent> findByRmaIdOrderById(Long rmaId);
}

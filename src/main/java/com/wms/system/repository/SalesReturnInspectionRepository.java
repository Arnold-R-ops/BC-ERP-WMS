package com.wms.system.repository;

import com.wms.system.entity.SalesReturnInspection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesReturnInspectionRepository extends JpaRepository<SalesReturnInspection, Long> {
    List<SalesReturnInspection> findByRmaIdOrderById(Long rmaId);
}

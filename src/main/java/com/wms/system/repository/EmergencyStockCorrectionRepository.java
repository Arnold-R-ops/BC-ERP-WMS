package com.wms.system.repository;

import com.wms.system.entity.EmergencyStockCorrection;
import com.wms.system.entity.enums.EmergencyCorrectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmergencyStockCorrectionRepository extends JpaRepository<EmergencyStockCorrection, Long> {
    boolean existsByCorrectionNo(String correctionNo);
    List<EmergencyStockCorrection> findByStatusOrderByCreatedAtDesc(EmergencyCorrectionStatus status);
}

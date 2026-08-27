package com.wms.system.repository;

import com.wms.system.entity.EmergencyStockCorrection;
import com.wms.system.entity.enums.EmergencyCorrectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmergencyStockCorrectionRepository extends JpaRepository<EmergencyStockCorrection, Long> {
    boolean existsByCorrectionNo(String correctionNo);
    List<EmergencyStockCorrection> findByStatusOrderByCreatedAtDesc(EmergencyCorrectionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select correction from EmergencyStockCorrection correction where correction.id = :id")
    Optional<EmergencyStockCorrection> findByIdForUpdate(@Param("id") Long id);
}

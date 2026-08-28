package com.wms.system.repository;

import com.wms.system.entity.OtherInboundApplication;
import com.wms.system.entity.enums.OtherInboundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface OtherInboundApplicationRepository extends JpaRepository<OtherInboundApplication, Long> {
    Page<OtherInboundApplication> findByStatus(OtherInboundStatus status, Pageable pageable);
    Page<OtherInboundApplication> findAllByOrderByIdDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from OtherInboundApplication application where application.id = :id")
    Optional<OtherInboundApplication> findByIdForUpdate(@Param("id") Long id);
}

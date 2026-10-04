package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformExportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PlatformExportJobRepository extends JpaRepository<PlatformExportJob, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select j from PlatformExportJob j where j.id=:id")
    Optional<PlatformExportJob> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<PlatformExportJob> findByPublicIdAndPlatformUserId(String publicId, Long platformUserId);
    List<PlatformExportJob> findByStatusAndExpiresAtBefore(String status, OffsetDateTime now);
    List<PlatformExportJob> findByTargetTenantId(Long targetTenantId);
}

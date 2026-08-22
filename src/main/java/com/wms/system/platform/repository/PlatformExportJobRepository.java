package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformExportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PlatformExportJobRepository extends JpaRepository<PlatformExportJob, Long> {
    Optional<PlatformExportJob> findByPublicIdAndPlatformUserId(String publicId, Long platformUserId);
    List<PlatformExportJob> findByStatusAndExpiresAtBefore(String status, OffsetDateTime now);
    List<PlatformExportJob> findByTargetTenantId(Long targetTenantId);
}

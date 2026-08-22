package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface PlatformAuditLogRepository extends JpaRepository<PlatformAuditLog, Long> {
    @Query("select a from PlatformAuditLog a where a.createdAt >= :from and a.createdAt <= :to "
        + "and (:tenantId is null or a.targetTenantId = :tenantId) "
        + "and (:action is null or a.action = :action)")
    Page<PlatformAuditLog> search(@Param("from") OffsetDateTime from,
                                  @Param("to") OffsetDateTime to,
                                  @Param("tenantId") Long tenantId,
                                  @Param("action") String action,
                                  Pageable pageable);
}

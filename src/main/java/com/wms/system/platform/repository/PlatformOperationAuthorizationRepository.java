package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformOperationAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;

public interface PlatformOperationAuthorizationRepository
    extends JpaRepository<PlatformOperationAuthorization, Long> {

    Optional<PlatformOperationAuthorization> findByPublicIdAndTenantId(String publicId, Long tenantId);
    Optional<PlatformOperationAuthorization> findByPublicId(String publicId);
    List<PlatformOperationAuthorization> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PlatformOperationAuthorization a where a.id = :id")
    Optional<PlatformOperationAuthorization> findByIdForUpdate(@Param("id") Long id);
}

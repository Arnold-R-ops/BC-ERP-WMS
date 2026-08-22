package com.wms.system.tenant.repository;

import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.model.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Tenant> findAllByStatusOrderByIdAsc(TenantStatus status);

    @Query("select t from Tenant t where t.status <> :excludedStatus "
        + "and (:status is null or t.status = :status) "
        + "and (:keywordApplied = false or lower(t.displayName) like lower(concat('%', :keyword, '%')) "
        + "or lower(t.tenantCode) like lower(concat('%', :keyword, '%')))")
    Page<Tenant> searchPlatformDirectory(@Param("keyword") String keyword,
        @Param("keywordApplied") boolean keywordApplied,
        @Param("status") TenantStatus status,
        @Param("excludedStatus") TenantStatus excludedStatus,
        Pageable pageable);

    @Query("select t.id from Tenant t where (t.status = :closed and t.purgeDueAt <= :now) "
        + "or t.status = :pending order by t.id")
    List<Long> findIdsDueForPurge(@Param("now") OffsetDateTime now,
        @Param("closed") TenantStatus closed, @Param("pending") TenantStatus pending);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.id = :id")
    Optional<Tenant> findByIdForUpdate(@Param("id") Long id);
}

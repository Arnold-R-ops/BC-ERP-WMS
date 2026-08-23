package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface PlatformAdminDirectoryRepository extends Repository<PlatformUser, Long> {

    Optional<PlatformUser> findById(Long id);

    @Query("""
        select u from PlatformUser u
        where (:keywordApplied = false
            or lower(u.normalizedEmail) like lower(concat('%', :keyword, '%'))
            or lower(u.displayName) like lower(concat('%', :keyword, '%')))
          and (:enabled is null or u.enabled = :enabled)
          and (:roleApplied = false or exists (
              select ur.id from PlatformUserRole ur, PlatformRole r
              where ur.platformUserId = u.id
                and r.id = ur.platformRoleId
                and r.roleCode = :roleCode
          ))
          and (:mfaStatus is null
            or (:mfaStatus = 'TEMPORARILY_LOCKED'
                and u.mfaLockedUntil is not null and u.mfaLockedUntil > :now)
            or (:mfaStatus = 'ENROLLED'
                and (u.mfaLockedUntil is null or u.mfaLockedUntil <= :now)
                and u.mfaEnabled = true and u.mfaEnrolledAt is not null)
            or (:mfaStatus = 'NOT_ENROLLED'
                and (u.mfaLockedUntil is null or u.mfaLockedUntil <= :now)
                and (u.mfaEnabled = false or u.mfaEnrolledAt is null)))
        """)
    Page<PlatformUser> search(
        @Param("keyword") String keyword,
        @Param("keywordApplied") boolean keywordApplied,
        @Param("enabled") Boolean enabled,
        @Param("roleCode") String roleCode,
        @Param("roleApplied") boolean roleApplied,
        @Param("mfaStatus") String mfaStatus,
        @Param("now") OffsetDateTime now,
        Pageable pageable
    );
}

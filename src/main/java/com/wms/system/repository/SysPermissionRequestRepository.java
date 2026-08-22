package com.wms.system.repository;

import com.wms.system.entity.SysPermissionRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SysPermissionRequestRepository extends JpaRepository<SysPermissionRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM SysPermissionRequest request WHERE request.id = :id")
    Optional<SysPermissionRequest> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM SysPermissionRequest request WHERE request.companyId = :companyId AND request.id = :id")
    Optional<SysPermissionRequest> findByCompanyIdAndIdForUpdate(
        @Param("companyId") Long companyId,
        @Param("id") Long id
    );

    Optional<SysPermissionRequest> findByCompanyIdAndId(Long companyId, Long id);

    boolean existsByTargetUserIdAndRequestedRoleIdAndStatus(
        Long targetUserId,
        Long requestedRoleId,
        String status
    );

    boolean existsByCompanyIdAndTargetUserIdAndRequestedRoleIdAndStatus(
        Long companyId, Long targetUserId, Long requestedRoleId, String status);

    @Query("""
        SELECT request
        FROM SysPermissionRequest request
        WHERE request.companyId = :companyId
          AND (:status IS NULL OR request.status = :status)
          AND (:targetUserId IS NULL OR request.targetUserId = :targetUserId)
          AND (:requestedRoleId IS NULL OR request.requestedRoleId = :requestedRoleId)
          AND (
              :includeProtected = true
              OR NOT EXISTS (
                  SELECT userRole.id
                  FROM SysUserRole userRole, SysRole role
                  WHERE userRole.userId = request.targetUserId
                    AND userRole.companyId = :companyId
                    AND role.companyId = :companyId
                    AND role.id = userRole.roleId
                    AND (
                        role.roleCode IN ('TENANT_ADMIN', 'SECURITY_ADMIN')
                        OR role.systemCategory = 'PRIVILEGED'
                    )
              )
          )
        """)
    Page<SysPermissionRequest> search(
        @Param("companyId") Long companyId,
        @Param("status") String status,
        @Param("targetUserId") Long targetUserId,
        @Param("requestedRoleId") Long requestedRoleId,
        @Param("includeProtected") boolean includeProtected,
        Pageable pageable
    );
}

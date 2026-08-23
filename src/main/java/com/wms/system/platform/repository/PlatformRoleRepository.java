package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlatformRoleRepository extends JpaRepository<PlatformRole, Long> {
    Optional<PlatformRole> findByRoleCode(String roleCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PlatformRole r where r.roleCode = :roleCode")
    Optional<PlatformRole> findByRoleCodeForUpdate(@Param("roleCode") String roleCode);
}

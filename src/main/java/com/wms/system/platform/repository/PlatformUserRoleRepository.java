package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformUserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformUserRoleRepository extends JpaRepository<PlatformUserRole, Long> {
    List<PlatformUserRole> findByPlatformUserId(Long platformUserId);

    @Query("select count(ur) from PlatformUserRole ur, PlatformUser u, PlatformRole r " +
        "where ur.platformUserId=u.id and ur.platformRoleId=r.id and r.roleCode=:roleCode and u.enabled=true")
    long countEnabledUsersByRoleCode(@Param("roleCode") String roleCode);
}

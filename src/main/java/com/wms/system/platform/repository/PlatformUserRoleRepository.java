package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformUserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformUserRoleRepository extends JpaRepository<PlatformUserRole, Long> {
    interface RoleView {
        Long getPlatformUserId();
        String getRoleCode();
        String getDisplayName();
    }

    List<PlatformUserRole> findByPlatformUserId(Long platformUserId);

    @Query("select r.roleCode from PlatformUserRole ur, PlatformRole r " +
        "where ur.platformRoleId=r.id and ur.platformUserId=:platformUserId order by r.roleCode")
    List<String> findRoleCodesByPlatformUserId(@Param("platformUserId") Long platformUserId);

    @Query("select ur.platformUserId as platformUserId, r.roleCode as roleCode, " +
        "r.displayName as displayName from PlatformUserRole ur, PlatformRole r " +
        "where ur.platformRoleId=r.id and ur.platformUserId in :userIds " +
        "order by ur.platformUserId, r.roleCode")
    List<RoleView> findRoleViewsByPlatformUserIdIn(@Param("userIds") Collection<Long> userIds);

    @Query("select count(ur) from PlatformUserRole ur, PlatformUser u, PlatformRole r " +
        "where ur.platformUserId=u.id and ur.platformRoleId=r.id and r.roleCode=:roleCode and u.enabled=true")
    long countEnabledUsersByRoleCode(@Param("roleCode") String roleCode);
}

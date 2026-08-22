package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformAccessGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;

public interface PlatformAccessGrantRepository extends JpaRepository<PlatformAccessGrant, Long> {
    @Query("select count(g) > 0 from PlatformAccessGrant g where g.granteePlatformUserId=:userId and g.capability=:capability and g.tenantId=:tenantId and g.datasetCode=:dataset and g.revokedAt is null and g.effectiveFrom <= :now and g.expiresAt > :now")
    boolean hasActive(@Param("userId") Long userId, @Param("capability") String capability, @Param("tenantId") Long tenantId, @Param("dataset") String dataset, @Param("now") OffsetDateTime now);
    @Query("select distinct g.tenantId from PlatformAccessGrant g where g.granteePlatformUserId=:userId and g.capability=:capability and g.revokedAt is null and g.effectiveFrom <= :now and g.expiresAt > :now")
    List<Long> activeTenantIds(@Param("userId") Long userId, @Param("capability") String capability, @Param("now") OffsetDateTime now);
    @Query("select g from PlatformAccessGrant g where g.granteePlatformUserId=:userId and g.revokedAt is null and g.effectiveFrom <= :now and g.expiresAt > :now order by g.tenantId, g.datasetCode, g.capability")
    List<PlatformAccessGrant> activeGrants(@Param("userId") Long userId, @Param("now") OffsetDateTime now);
    List<PlatformAccessGrant> findByGranteePlatformUserIdOrderByCreatedAtDesc(Long userId);
}

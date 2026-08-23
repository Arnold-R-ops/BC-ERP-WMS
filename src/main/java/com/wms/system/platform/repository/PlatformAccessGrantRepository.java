package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformAccessGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface PlatformAccessGrantRepository extends JpaRepository<PlatformAccessGrant, Long> {
    interface ActiveGrantCountView {
        Long getPlatformUserId();
        String getCapability();
        Long getGrantCount();
    }

    @Query("select count(g) > 0 from PlatformAccessGrant g where g.granteePlatformUserId=:userId and g.capability=:capability and g.tenantId=:tenantId and g.datasetCode=:dataset and g.revokedAt is null and g.effectiveFrom <= :now and g.expiresAt > :now")
    boolean hasActive(@Param("userId") Long userId, @Param("capability") String capability, @Param("tenantId") Long tenantId, @Param("dataset") String dataset, @Param("now") OffsetDateTime now);
    @Query("select distinct g.tenantId from PlatformAccessGrant g where g.granteePlatformUserId=:userId and g.capability=:capability and g.revokedAt is null and g.effectiveFrom <= :now and g.expiresAt > :now")
    List<Long> activeTenantIds(@Param("userId") Long userId, @Param("capability") String capability, @Param("now") OffsetDateTime now);
    @Query("select g from PlatformAccessGrant g where g.granteePlatformUserId=:userId and g.revokedAt is null and g.effectiveFrom <= :now and g.expiresAt > :now order by g.tenantId, g.datasetCode, g.capability")
    List<PlatformAccessGrant> activeGrants(@Param("userId") Long userId, @Param("now") OffsetDateTime now);
    List<PlatformAccessGrant> findByGranteePlatformUserIdOrderByCreatedAtDesc(Long userId);

    @Query("select g.granteePlatformUserId as platformUserId, g.capability as capability, " +
        "count(g) as grantCount from PlatformAccessGrant g " +
        "where g.granteePlatformUserId in :userIds and g.revokedAt is null " +
        "and g.effectiveFrom <= :now and g.expiresAt > :now " +
        "group by g.granteePlatformUserId, g.capability")
    List<ActiveGrantCountView> countActiveByPlatformUserIds(
        @Param("userIds") Collection<Long> userIds,
        @Param("now") OffsetDateTime now
    );

    @Query("select count(g) from PlatformAccessGrant g " +
        "where g.granteePlatformUserId=:userId and g.revokedAt is null " +
        "and g.effectiveFrom <= :now and g.expiresAt > :now")
    long countActiveByPlatformUserId(
        @Param("userId") Long userId,
        @Param("now") OffsetDateTime now
    );

    @Query("select count(g) from PlatformAccessGrant g " +
        "where g.granteePlatformUserId=:userId and g.revokedAt is null " +
        "and g.expiresAt > :now")
    long countCurrentOrFutureByPlatformUserId(
        @Param("userId") Long userId,
        @Param("now") OffsetDateTime now
    );
}

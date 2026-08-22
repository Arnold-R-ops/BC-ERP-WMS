package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformMfaChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.OffsetDateTime;

public interface PlatformMfaChallengeRepository extends JpaRepository<PlatformMfaChallenge, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PlatformMfaChallenge c where c.tokenHash = :tokenHash")
    Optional<PlatformMfaChallenge> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("delete from PlatformMfaChallenge c where c.consumedAt is not null or c.expiresAt < :cutoff")
    int deleteConsumedOrExpired(@Param("cutoff") OffsetDateTime cutoff);

    @Modifying
    @Query("delete from PlatformMfaChallenge c where c.platformUserId = :platformUserId")
    int deleteByPlatformUserId(@Param("platformUserId") Long platformUserId);
}

package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, Long> {
    Optional<PlatformUser> findByNormalizedEmail(String normalizedEmail);

    Optional<PlatformUser> findByNormalizedEmailAndEnabledTrue(String normalizedEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from PlatformUser u where u.id = :id")
    Optional<PlatformUser> findByIdForUpdate(@Param("id") Long id);
}

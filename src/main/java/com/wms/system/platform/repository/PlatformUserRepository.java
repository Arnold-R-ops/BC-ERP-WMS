package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, Long> {
    Optional<PlatformUser> findByNormalizedEmail(String normalizedEmail);

    Optional<PlatformUser> findByNormalizedEmailAndEnabledTrue(String normalizedEmail);
}

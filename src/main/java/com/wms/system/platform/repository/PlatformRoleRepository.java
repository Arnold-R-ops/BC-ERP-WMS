package com.wms.system.platform.repository;

import com.wms.system.platform.model.PlatformRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformRoleRepository extends JpaRepository<PlatformRole, Long> {
    Optional<PlatformRole> findByRoleCode(String roleCode);
}

package com.wms.system.repository;

import com.wms.system.entity.SysApprovalTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysApprovalTemplateRepository extends JpaRepository<SysApprovalTemplate, Long> {
    Optional<SysApprovalTemplate> findByCompanyIdAndTemplateCode(Long companyId, String templateCode);
}

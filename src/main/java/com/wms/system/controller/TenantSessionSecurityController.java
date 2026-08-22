package com.wms.system.controller;

import com.wms.system.dto.TenantSessionSecurityAuditDTO;
import com.wms.system.repository.TenantSessionSecurityAuditRepository;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Tenant-scoped read access to explicit session-revocation audit records. */
@RestController
@RequestMapping("/api/session-security")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class TenantSessionSecurityController {

    private static final int MAX_LIMIT = 200;

    private final TenantSessionSecurityAuditRepository auditRepository;

    @GetMapping("/audits")
    @Transactional(readOnly = true)
    public ResponseEntity<List<TenantSessionSecurityAuditDTO>> listAudits(
        @RequestParam(defaultValue = "100") int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<TenantSessionSecurityAuditDTO> audits = auditRepository
            .findByCompanyIdOrderByCreatedAtDesc(
                CompanyScope.currentCompanyId(),
                PageRequest.of(0, safeLimit)
            )
            .stream()
            .map(TenantSessionSecurityAuditDTO::from)
            .toList();
        return ResponseEntity.ok(audits);
    }
}

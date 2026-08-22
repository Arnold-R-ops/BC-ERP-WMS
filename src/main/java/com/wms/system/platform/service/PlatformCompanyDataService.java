package com.wms.system.platform.service;

import com.wms.system.platform.config.PlatformAccessProperties;
import com.wms.system.platform.dto.PlatformCompanyResponse;
import com.wms.system.platform.dto.PlatformDataPage;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.repository.PlatformTenantDirectoryRepository;
import com.wms.system.security.PlatformSecurityUser;
import com.wms.system.tenant.context.*;
import com.wms.system.tenant.model.*;
import com.wms.system.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlatformCompanyDataService {
    private final TenantRepository tenantRepository;
    private final PlatformTenantDirectoryRepository platformTenantDirectoryRepository;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;
    private final PlatformAccessProperties properties;
    private final PlatformAccessGuard guard;
    private final PlatformAuditService auditService;

    public Page<PlatformCompanyResponse> listCompanies(String keyword, TenantStatus status, int page, int size,
                                                        HttpServletRequest request) {
        PlatformSecurityUser actor = guard.requireTenantDirectory();
        String safeKeyword = normalizeKeyword(keyword);
        if (status == TenantStatus.PURGED) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("status", status.name()));
        }
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), properties.getMaxPageSize()),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        boolean keywordApplied = safeKeyword != null;
        String queryKeyword = keywordApplied ? safeKeyword : "";
        Page<PlatformCompanyResponse> result;
        if (guard.isSuperAdmin()) {
            result = tenantRepository.searchPlatformDirectory(queryKeyword, keywordApplied, status,
                TenantStatus.PURGED, pageable)
                .map(PlatformCompanyResponse::from);
        } else {
            List<String> capabilities = new ArrayList<>();
            if (guard.hasAuthority("ROLE_PLATFORM_TENANT_READ")) capabilities.add("READ");
            if (guard.hasAuthority("ROLE_PLATFORM_TENANT_EXPORT")) capabilities.add("EXPORT");
            result = platformTenantDirectoryRepository.searchAuthorized(actor.getId(), capabilities,
                java.time.OffsetDateTime.now(), queryKeyword, keywordApplied, status, TenantStatus.PURGED, pageable)
                .map(PlatformCompanyResponse::from);
        }
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("page", pageable.getPageNumber());
        auditDetail.put("size", pageable.getPageSize());
        auditDetail.put("returned", result.getNumberOfElements());
        auditDetail.put("total", result.getTotalElements());
        auditDetail.put("keywordApplied", keywordApplied);
        if (keywordApplied) auditDetail.put("keywordLength", safeKeyword.length());
        if (status != null) auditDetail.put("status", status.name());
        auditService.record(actor.getId(), null, "READ", "tenant_directory", "SUCCESS", auditDetail, request);
        return result;
    }

    public Page<PlatformCompanyResponse> listCompanies(int page, int size, HttpServletRequest request) {
        return listCompanies(null, null, page, size, request);
    }

    public PlatformCompanyResponse company(Long companyId, HttpServletRequest request) {
        PlatformSecurityUser actor = guard.requirePlatformUser();
        try {
            guard.requireTenantListed(companyId);
            Tenant tenant = requireAvailable(companyId);
            auditService.record(actor.getId(), companyId, "READ", "tenant_overview", "SUCCESS", Map.of(), request);
            return PlatformCompanyResponse.from(tenant);
        } catch (RuntimeException exception) {
            auditService.record(actor.getId(), companyId, "READ", "tenant_overview", "FAILED",
                Map.of("error", exception.getClass().getSimpleName()), request);
            throw exception;
        }
    }

    public PlatformDataPage read(Long companyId, String resource, int page, int size,
                                 HttpServletRequest request) {
        PlatformSecurityUser actor = guard.requireRead(companyId, resource);
        Tenant tenant = requireAvailable(companyId);
        PlatformDatasetCatalog.Dataset dataset = PlatformDatasetCatalog.find(resource)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported platform dataset"));
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), properties.getMaxPageSize());
        try {
            PlatformDataPage result = TenantContextHolder.runWithResolvedTenant(context(tenant), () -> {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                return transaction.execute(status -> {
                    long total = entityManager.createQuery(dataset.countJpql(), Long.class).getSingleResult();
                    List<Object[]> rows = entityManager.createQuery(dataset.selectJpql(), Object[].class)
                        .setFirstResult(safePage * safeSize).setMaxResults(safeSize).getResultList();
                    List<Map<String, Object>> content = rows.stream().map(row -> map(dataset.headers(), row)).toList();
                    return new PlatformDataPage(resource, safePage, safeSize, total,
                        (int) Math.ceil(total / (double) safeSize), content);
                });
            });
            auditService.record(actor.getId(), companyId, "READ", resource, "SUCCESS",
                Map.of("page", safePage, "size", safeSize, "returned", result.content().size()), request);
            return result;
        } catch (RuntimeException exception) {
            auditService.record(actor.getId(), companyId, "READ", resource, "FAILED",
                Map.of("error", exception.getClass().getSimpleName()), request);
            throw exception;
        }
    }

    private Map<String, Object> map(List<String> headers, Object[] row) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) mapped.put(headers.get(i), row[i]);
        return mapped;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String normalized = keyword.trim();
        if (normalized.length() > 160) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("keywordMaxLength", 160));
        }
        return normalized;
    }

    public Tenant requireAvailable(Long companyId) {
        return tenantRepository.findById(companyId)
            .filter(tenant -> tenant.getStatus() != TenantStatus.PURGED)
            .orElseThrow(() -> new NoSuchElementException("Company data is unavailable"));
    }

    public static TenantContext context(Tenant tenant) {
        return new TenantContext(tenant.getId(), tenant.getSlug(), tenant.getSlug() + ".bcwms.com",
            RequestSurface.TENANT, tenant.getStatus());
    }
}

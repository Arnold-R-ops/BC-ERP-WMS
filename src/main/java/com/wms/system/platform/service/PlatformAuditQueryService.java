package com.wms.system.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.platform.dto.PlatformAuditLogResponse;
import com.wms.system.platform.dto.PlatformCompanyResponse;
import com.wms.system.platform.model.PlatformAuditLog;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformAuditLogRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.security.PlatformSecurityUser;
import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformAuditQueryService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_RANGE_DAYS = 31;
    private static final java.util.Set<String> SAFE_DETAIL_KEYS = java.util.Set.of(
        "page", "size", "returned", "total", "jobId", "recordCount",
        "authorizationId", "operation", "resourceId", "attempts", "locked",
        "targetPlatformUserId", "targetSuperAdmin", "sessionsRevoked",
        "backgroundTasksPreserved", "reason", "invitationId", "roleCode", "expiresAt",
        "keywordApplied", "keywordLength", "enabled", "roleApplied", "mfaStatus", "errorKey",
        "desiredEnabled", "targetSecurityVersion", "beforeEnabled", "afterEnabled", "changed",
        "activeGrantCount", "securityVersion", "idempotencyKeyHash", "invitationType",
        "desiredRole", "targetEnabled", "enabledPreserved", "accessGrantsPreserved"
    );

    private final PlatformAuditLogRepository auditRepository;
    private final PlatformUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PlatformAccessGuard guard;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<PlatformAuditLogResponse> search(OffsetDateTime from, OffsetDateTime to,
                                                  Long tenantId, String action,
                                                  int page, int size,
                                                  HttpServletRequest request) {
        PlatformSecurityUser actor = guard.requireAuditRead();
        OffsetDateTime safeTo = to == null ? OffsetDateTime.now() : to;
        OffsetDateTime safeFrom = from == null ? safeTo.minusDays(7) : from;
        if (safeFrom.isAfter(safeTo)) throw new IllegalArgumentException("Audit start time must not follow end time");
        if (safeFrom.isBefore(safeTo.minusDays(MAX_RANGE_DAYS))) {
            throw new IllegalArgumentException("Audit range cannot exceed 31 days");
        }
        String safeAction = normalizeAction(action);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<PlatformAuditLog> logs = auditRepository.search(safeFrom, safeTo, tenantId, safeAction, pageable);
        Map<Long, String> actors = userRepository.findAllById(logs.map(PlatformAuditLog::getPlatformUserId).toSet())
            .stream().collect(Collectors.toMap(PlatformUser::getId, PlatformUser::getNormalizedEmail));
        Map<Long, String> tenants = tenantRepository.findAllById(logs.stream()
                .map(PlatformAuditLog::getTargetTenantId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
            .stream().collect(Collectors.toMap(Tenant::getId, PlatformCompanyResponse::displayNameForPlatform));
        Page<PlatformAuditLogResponse> result = logs.map(log -> response(log, actors, tenants));
        auditService.record(actor.getId(), null, "AUDIT_READ", "platform_audit_log", "SUCCESS",
            Map.of("page", pageable.getPageNumber(), "size", pageable.getPageSize(),
                "returned", result.getNumberOfElements(), "total", result.getTotalElements()), request);
        return result;
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) return null;
        String result = action.trim().toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z_]{1,30}")) throw new IllegalArgumentException("Invalid audit action");
        return result;
    }

    private PlatformAuditLogResponse response(PlatformAuditLog log, Map<Long, String> actors,
                                               Map<Long, String> tenants) {
        return new PlatformAuditLogResponse(log.getId(), log.getCreatedAt(),
            actors.getOrDefault(log.getPlatformUserId(), "DEACTIVATED_PLATFORM_ACCOUNT"),
            log.getTargetTenantId(), log.getTargetTenantId() == null
                ? ("tenant_directory".equals(log.getResourceType()) ? "TENANT_DIRECTORY_PAGE" : "PLATFORM_SCOPE")
                : tenants.getOrDefault(log.getTargetTenantId(), "REMOVED_TENANT"),
            log.getAction(), log.getResourceType(), log.getResult(), safeSummary(log.getDetailJson()));
    }

    private Map<String, Object> safeSummary(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> source = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, value) -> {
                if (SAFE_DETAIL_KEYS.contains(key) && isSafeScalar(value)) result.put(key, value);
            });
            return result;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private boolean isSafeScalar(Object value) {
        return value == null || value instanceof String || value instanceof Number || value instanceof Boolean;
    }
}

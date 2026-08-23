package com.wms.system.platform.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.dto.PlatformAdminDetailResponse;
import com.wms.system.platform.dto.PlatformAdminRoleResponse;
import com.wms.system.platform.dto.PlatformAdminSummaryResponse;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformAccessGrantRepository;
import com.wms.system.platform.repository.PlatformAdminDirectoryRepository;
import com.wms.system.platform.repository.PlatformUserRoleRepository;
import com.wms.system.security.PlatformSecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformAdminDirectoryService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final String SUPER_ADMIN = "PLATFORM_SUPER_ADMIN";
    private static final String OPERATIONS_ADMIN = "PLATFORM_OPERATIONS_ADMIN";
    private static final String SECURITY_AUDITOR = "PLATFORM_SECURITY_AUDITOR";
    private static final String READ_CAPABILITY = "READ";
    private static final String EXPORT_CAPABILITY = "EXPORT";

    private final PlatformAdminDirectoryRepository directory;
    private final PlatformUserRoleRepository userRoles;
    private final PlatformAccessGrantRepository accessGrants;
    private final PlatformAccessGuard guard;
    private final PlatformAuditService audit;

    @Transactional(readOnly = true)
    public Page<PlatformAdminSummaryResponse> list(
        String keyword,
        Boolean enabled,
        String role,
        String mfaStatus,
        int page,
        int size,
        HttpServletRequest request
    ) {
        PlatformSecurityUser actor = guard.requireAdminDirectoryRead();
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        try {
            String safeKeyword = normalizeKeyword(keyword);
            String safeRole = normalizeRole(role);
            MfaStatus safeMfaStatus = normalizeMfaStatus(mfaStatus);
            OffsetDateTime now = OffsetDateTime.now();
            Pageable pageable = PageRequest.of(Math.max(0, page), safePageSize(size),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

            boolean keywordApplied = safeKeyword != null;
            boolean roleApplied = safeRole != null;
            Page<PlatformUser> users = directory.search(
                keywordApplied ? safeKeyword : "",
                keywordApplied,
                enabled,
                roleApplied ? safeRole : "",
                roleApplied,
                safeMfaStatus == null ? null : safeMfaStatus.name(),
                now,
                pageable
            );
            PageContext context = context(users.getContent(), now, actor.getId());
            Page<PlatformAdminSummaryResponse> result = users.map(user -> summary(user, context));

            auditDetail.put("page", pageable.getPageNumber());
            auditDetail.put("size", pageable.getPageSize());
            auditDetail.put("returned", result.getNumberOfElements());
            auditDetail.put("total", result.getTotalElements());
            auditDetail.put("keywordApplied", keywordApplied);
            if (keywordApplied) auditDetail.put("keywordLength", safeKeyword.length());
            if (enabled != null) auditDetail.put("enabled", enabled);
            auditDetail.put("roleApplied", roleApplied);
            if (roleApplied) auditDetail.put("roleCode", safeRole);
            if (safeMfaStatus != null) auditDetail.put("mfaStatus", safeMfaStatus.name());
            audit.record(actor.getId(), null, "ADMIN_DIRECTORY_READ", "platform_admin_directory",
                "SUCCESS", auditDetail, request);
            return result;
        } catch (BusinessException exception) {
            auditDetail.put("errorKey", exception.getErrorKey());
            audit.record(actor.getId(), null, "ADMIN_DIRECTORY_READ", "platform_admin_directory",
                "FAILED", auditDetail, request);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PlatformAdminDetailResponse detail(Long targetUserId, HttpServletRequest request) {
        PlatformSecurityUser actor = guard.requireAdminDirectoryRead();
        try {
            PlatformUser user = directory.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorKeys.PLATFORM_ADMIN_NOT_FOUND,
                    Map.of("targetUserId", targetUserId)));
            OffsetDateTime now = OffsetDateTime.now();
            PageContext context = context(List.of(user), now, actor.getId());
            PlatformAdminSummaryResponse summary = summary(user, context);
            GrantCounts counts = context.grantCounts().getOrDefault(user.getId(), GrantCounts.EMPTY);
            Map<String, Long> countsByCapability = new LinkedHashMap<>();
            countsByCapability.put(READ_CAPABILITY, counts.read());
            countsByCapability.put(EXPORT_CAPABILITY, counts.export());
            PlatformAdminDetailResponse result = new PlatformAdminDetailResponse(
                summary.id(), summary.displayName(), summary.email(), summary.enabled(), summary.roles(),
                summary.mfaStatus(), summary.mfaEnrolledAt(), summary.mfaLockedUntil(), summary.createdAt(),
                summary.updatedAt(), summary.activeGrantCount(), Map.copyOf(countsByCapability),
                summary.currentUser(), summary.lastEnabledSuperAdmin()
            );
            audit.record(actor.getId(), null, "ADMIN_DIRECTORY_READ", "platform_admin_detail",
                "SUCCESS", Map.of("targetPlatformUserId", targetUserId), request);
            return result;
        } catch (BusinessException exception) {
            audit.record(actor.getId(), null, "ADMIN_DIRECTORY_READ", "platform_admin_detail",
                "FAILED", Map.of("targetPlatformUserId", targetUserId, "errorKey", exception.getErrorKey()),
                request);
            throw exception;
        }
    }

    private PageContext context(Collection<PlatformUser> users, OffsetDateTime now, Long actorId) {
        List<Long> userIds = users.stream().map(PlatformUser::getId).toList();
        Map<Long, List<PlatformAdminRoleResponse>> roles = loadRoles(userIds);
        Map<Long, GrantCounts> counts = loadGrantCounts(userIds, now);
        long enabledSuperAdminCount = userRoles.countEnabledUsersByRoleCode(SUPER_ADMIN);
        return new PageContext(roles, counts, enabledSuperAdminCount, actorId, now);
    }

    private Map<Long, List<PlatformAdminRoleResponse>> loadRoles(List<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Map<Long, List<PlatformUserRoleRepository.RoleView>> grouped = userRoles
            .findRoleViewsByPlatformUserIdIn(userIds).stream()
            .collect(java.util.stream.Collectors.groupingBy(
                PlatformUserRoleRepository.RoleView::getPlatformUserId,
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));
        Map<Long, List<PlatformAdminRoleResponse>> result = new LinkedHashMap<>();
        grouped.forEach((userId, roleViews) -> result.put(userId,
            roleViews.stream().map(this::roleResponse).toList()));
        return result;
    }

    private Map<Long, GrantCounts> loadGrantCounts(List<Long> userIds, OffsetDateTime now) {
        if (userIds.isEmpty()) return Map.of();
        Map<Long, MutableGrantCounts> mutable = new LinkedHashMap<>();
        for (PlatformAccessGrantRepository.ActiveGrantCountView row
            : accessGrants.countActiveByPlatformUserIds(userIds, now)) {
            MutableGrantCounts counts = mutable.computeIfAbsent(row.getPlatformUserId(), ignored -> new MutableGrantCounts());
            counts.add(row.getCapability(), row.getGrantCount() == null ? 0L : row.getGrantCount());
        }
        Map<Long, GrantCounts> result = new LinkedHashMap<>();
        mutable.forEach((userId, counts) -> result.put(userId, counts.snapshot()));
        return result;
    }

    private PlatformAdminSummaryResponse summary(PlatformUser user, PageContext context) {
        List<PlatformAdminRoleResponse> roles = context.roles().getOrDefault(user.getId(), List.of());
        GrantCounts counts = context.grantCounts().getOrDefault(user.getId(), GrantCounts.EMPTY);
        OffsetDateTime activeLock = activeLock(user, context.now());
        boolean enrolled = Boolean.TRUE.equals(user.getMfaEnabled()) && user.getMfaEnrolledAt() != null;
        String mfaStatus = activeLock != null
            ? MfaStatus.TEMPORARILY_LOCKED.name()
            : enrolled ? MfaStatus.ENROLLED.name() : MfaStatus.NOT_ENROLLED.name();
        boolean superAdmin = roles.stream().anyMatch(role -> SUPER_ADMIN.equals(role.code()));
        boolean enabled = Boolean.TRUE.equals(user.getEnabled());
        return new PlatformAdminSummaryResponse(
            user.getId(), user.getDisplayName(), user.getNormalizedEmail(), enabled, roles,
            mfaStatus, enrolled ? user.getMfaEnrolledAt() : null, activeLock,
            user.getCreatedAt(), user.getUpdatedAt(), counts.total(),
            user.getId().equals(context.actorId()),
            enabled && superAdmin && context.enabledSuperAdminCount() == 1
        );
    }

    private PlatformAdminRoleResponse roleResponse(PlatformUserRoleRepository.RoleView role) {
        return switch (role.getRoleCode()) {
            case SUPER_ADMIN -> new PlatformAdminRoleResponse(
                SUPER_ADMIN, "平台超级管理员", "JOB_ROLE");
            case OPERATIONS_ADMIN -> new PlatformAdminRoleResponse(
                OPERATIONS_ADMIN, "平台运营管理员", "JOB_ROLE");
            case SECURITY_AUDITOR -> new PlatformAdminRoleResponse(
                SECURITY_AUDITOR, "安全审计员", "JOB_ROLE");
            case "PLATFORM_TENANT_READ" -> new PlatformAdminRoleResponse(
                "PLATFORM_TENANT_READ", "租户数据读取能力", "TECHNICAL_CAPABILITY");
            case "PLATFORM_TENANT_EXPORT" -> new PlatformAdminRoleResponse(
                "PLATFORM_TENANT_EXPORT", "租户数据导出能力", "TECHNICAL_CAPABILITY");
            default -> new PlatformAdminRoleResponse(
                role.getRoleCode(), role.getDisplayName(), "UNCLASSIFIED");
        };
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String normalized = keyword.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "keyword", "maxLength", MAX_KEYWORD_LENGTH));
        }
        return normalized;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) return null;
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_]{1,50}")) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", "role"));
        }
        return normalized;
    }

    private MfaStatus normalizeMfaStatus(String mfaStatus) {
        if (mfaStatus == null || mfaStatus.isBlank()) return null;
        try {
            return MfaStatus.valueOf(mfaStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", "mfaStatus"));
        }
    }

    private int safePageSize(int size) {
        int requested = size == 0 ? DEFAULT_PAGE_SIZE : size;
        return Math.min(Math.max(1, requested), MAX_PAGE_SIZE);
    }

    private OffsetDateTime activeLock(PlatformUser user, OffsetDateTime now) {
        return user.getMfaLockedUntil() != null && user.getMfaLockedUntil().isAfter(now)
            ? user.getMfaLockedUntil() : null;
    }

    private enum MfaStatus {
        NOT_ENROLLED,
        ENROLLED,
        TEMPORARILY_LOCKED
    }

    private record PageContext(
        Map<Long, List<PlatformAdminRoleResponse>> roles,
        Map<Long, GrantCounts> grantCounts,
        long enabledSuperAdminCount,
        Long actorId,
        OffsetDateTime now
    ) { }

    private record GrantCounts(long read, long export, long other) {
        private static final GrantCounts EMPTY = new GrantCounts(0L, 0L, 0L);

        private long total() {
            return read + export + other;
        }
    }

    private static final class MutableGrantCounts {
        private long read;
        private long export;
        private long other;

        private void add(String capability, long count) {
            if (READ_CAPABILITY.equals(capability)) read += count;
            else if (EXPORT_CAPABILITY.equals(capability)) export += count;
            else other += count;
        }

        private GrantCounts snapshot() {
            return new GrantCounts(read, export, other);
        }
    }
}

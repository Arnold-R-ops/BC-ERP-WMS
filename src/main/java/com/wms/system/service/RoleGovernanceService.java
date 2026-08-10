package com.wms.system.service;

import com.wms.system.dto.RoleGovernanceAuditDTO;
import com.wms.system.dto.RoleGovernanceResult;
import com.wms.system.dto.RolePackageCreateRequest;
import com.wms.system.dto.RolePackageDraftRequest;
import com.wms.system.dto.RoleReviewRequest;
import com.wms.system.dto.RoleReviewSubmitRequest;
import com.wms.system.dto.RoleRuntimeStatusRequest;
import com.wms.system.entity.SysApprovalTemplate;
import com.wms.system.entity.SysPermission;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysRoleGovernanceAudit;
import com.wms.system.entity.SysRolePermission;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysApprovalTemplateRepository;
import com.wms.system.repository.SysPermissionRepository;
import com.wms.system.repository.SysRoleGovernanceAuditRepository;
import com.wms.system.repository.SysRoleInheritRepository;
import com.wms.system.repository.SysRolePermissionRepository;
import com.wms.system.repository.SysRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Lifecycle commands for custom permission packages using the built-in simple approval template. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleGovernanceService {

    private static final String ACTION_CREATE_DRAFT = "CREATE_DRAFT";
    private static final String ACTION_UPDATE_DRAFT = "UPDATE_DRAFT";
    private static final String ACTION_SUBMIT_REVIEW = "SUBMIT_REVIEW";
    private static final String ACTION_APPROVE = "APPROVE";
    private static final String ACTION_REJECT = "REJECT";
    private static final String ACTION_ACTIVATE = "ACTIVATE";
    private static final String ACTION_DEACTIVATE = "DEACTIVATE";

    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;
    private final SysRolePermissionRepository rolePermissionRepository;
    private final SysRoleInheritRepository roleInheritRepository;
    private final SysApprovalTemplateRepository approvalTemplateRepository;
    private final SysRoleGovernanceAuditRepository auditRepository;
    private final RoleService roleService;
    private final PermissionCacheService cacheService;
    private final SecurityVersionService securityVersionService;

    @Transactional(rollbackFor = Exception.class)
    public RoleGovernanceResult createDraft(
            RolePackageCreateRequest request,
            Long operatorId,
            String operatorUsername
    ) {
        String roleCode = request.getRoleCode().trim().toUpperCase(Locale.ROOT);
        if (roleRepository.existsByRoleCode(roleCode)) {
            throw new BusinessException(ErrorKeys.ROLE_COPY_TARGET_EXISTS, Map.of(
                    "roleCode", roleCode
            ));
        }

        SysRole draft = SysRole.builder()
                .roleCode(roleCode)
                .roleName(request.getRoleName().trim())
                .description(request.getDescription().trim())
                .roleType(SysRole.ROLE_TYPE_CUSTOM)
                .systemCategory(null)
                .importAllowed(true)
                .approvalTemplateCode(SysRole.SIMPLE_APPROVAL_TEMPLATE_CODE)
                .reviewStatus(SysRole.REVIEW_STATUS_DRAFT)
                .status("DISABLED")
                .sortOrder(1000)
                .build();

        final SysRole savedDraft;
        try {
            savedDraft = roleRepository.saveAndFlush(draft);
        } catch (DataIntegrityViolationException duplicate) {
            throw new BusinessException(ErrorKeys.ROLE_COPY_TARGET_EXISTS, Map.of(
                    "roleCode", roleCode
            ));
        }

        Snapshot snapshot = requireAssignablePermissions(savedDraft, request.getPermissionIds());
        List<SysRolePermission> assignments = snapshot.permissions().stream()
                .map(permission -> SysRolePermission.builder()
                        .companyId(savedDraft.getCompanyId())
                        .roleId(savedDraft.getId())
                        .permissionId(permission.getId())
                        .grantedBy(operatorId)
                        .build())
                .toList();
        rolePermissionRepository.saveAll(assignments);

        SysRoleGovernanceAudit audit = saveAudit(
                savedDraft,
                ACTION_CREATE_DRAFT,
                operatorId,
                operatorUsername,
                null,
                savedDraft.getReviewStatus(),
                null,
                savedDraft.getStatus(),
                snapshot,
                emptyToNull(snapshot.permissionCodes()),
                null,
                savedDraft.getDescription()
        );
        log.info("Blank permission-package draft created: role={}, permissions={}, highRisk={}, operator={}",
                savedDraft.getRoleCode(), snapshot.permissions().size(), snapshot.highRiskCount(),
                normalizeOperator(operatorUsername));
        return result(savedDraft, ACTION_CREATE_DRAFT, audit, snapshot);
    }

    @Transactional(rollbackFor = Exception.class)
    public RoleGovernanceResult updateDraft(
            Long roleId,
            RolePackageDraftRequest request,
            Long operatorId,
            String operatorUsername
    ) {
        SysRole role = requireCustomForUpdate(roleId);
        if (role.isActive()) {
            throw stateError(ErrorKeys.ROLE_PACKAGE_ACTIVE_IMMUTABLE, role);
        }
        if (!role.isDraft()) {
            throw stateError(ErrorKeys.ROLE_PACKAGE_NOT_DRAFT, role);
        }
        if (!roleInheritRepository.findParentRoleIdsByChildRoleId(roleId).isEmpty()) {
            throw new BusinessException(ErrorKeys.ROLE_PACKAGE_INHERITANCE_EDIT_UNSUPPORTED, Map.of(
                    "roleId", roleId,
                    "roleCode", role.getRoleCode()
            ));
        }

        Set<Long> oldIds = rolePermissionRepository.findPermissionIdsByRoleId(roleId);
        Snapshot snapshot = requireAssignablePermissions(role, request.getPermissionIds());
        Set<Long> newIds = snapshot.permissions().stream()
                .map(SysPermission::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> addedIds = new LinkedHashSet<>(newIds);
        addedIds.removeAll(oldIds);
        Set<Long> removedIds = new LinkedHashSet<>(oldIds);
        removedIds.removeAll(newIds);

        role.setRoleName(request.getRoleName().trim());
        role.setDescription(request.getDescription().trim());
        role.setReviewedBy(null);
        role.setReviewedByUsername(null);
        role.setReviewedAt(null);
        role.setReviewComment(null);
        roleRepository.save(role);

        rolePermissionRepository.deleteByRoleId(roleId);
        rolePermissionRepository.flush();
        List<SysRolePermission> assignments = snapshot.permissions().stream()
                .map(permission -> SysRolePermission.builder()
                        .companyId(role.getCompanyId())
                        .roleId(roleId)
                        .permissionId(permission.getId())
                        .grantedBy(operatorId)
                        .build())
                .toList();
        rolePermissionRepository.saveAll(assignments);

        List<SysPermission> changedPermissions = permissionRepository.findByIdIn(union(addedIds, removedIds));
        String addedCodes = codesForIds(changedPermissions, addedIds);
        String removedCodes = codesForIds(changedPermissions, removedIds);
        SysRoleGovernanceAudit audit = saveAudit(
                role,
                ACTION_UPDATE_DRAFT,
                operatorId,
                operatorUsername,
                role.getReviewStatus(),
                role.getReviewStatus(),
                role.getStatus(),
                role.getStatus(),
                snapshot,
                addedCodes,
                removedCodes,
                normalizeOptional(request.getReason())
        );
        evictRoleSecurity(roleId);
        return result(role, ACTION_UPDATE_DRAFT, audit, snapshot);
    }

    @Transactional(rollbackFor = Exception.class)
    public RoleGovernanceResult submitForReview(
            Long roleId,
            RoleReviewSubmitRequest request,
            Long operatorId,
            String operatorUsername
    ) {
        SysRole role = requireCustomForUpdate(roleId);
        if (role.isActive()) {
            throw stateError(ErrorKeys.ROLE_PACKAGE_ACTIVE_IMMUTABLE, role);
        }
        if (!role.isDraft()) {
            throw stateError(ErrorKeys.ROLE_PACKAGE_NOT_DRAFT, role);
        }
        requireSimpleTemplate(role);
        Snapshot snapshot = requireAssignablePermissions(
                role,
                rolePermissionRepository.findPermissionIdsByRoleId(roleId)
        );
        String reason = normalizeOptional(request.getReason());
        if (snapshot.highRiskCount() > 0 && reason == null) {
            throw new BusinessException(ErrorKeys.ROLE_PACKAGE_HIGH_RISK_REASON_REQUIRED, Map.of(
                    "roleId", roleId,
                    "roleCode", role.getRoleCode(),
                    "highRiskCount", snapshot.highRiskCount()
            ));
        }

        String fromReviewStatus = role.getReviewStatus();
        role.setReviewStatus(SysRole.REVIEW_STATUS_PENDING);
        role.setReviewSubmittedBy(operatorId);
        role.setReviewSubmittedByUsername(normalizeOperator(operatorUsername));
        role.setReviewSubmittedAt(LocalDateTime.now());
        role.setReviewedBy(null);
        role.setReviewedByUsername(null);
        role.setReviewedAt(null);
        role.setReviewComment(null);
        roleRepository.save(role);

        SysRoleGovernanceAudit audit = saveAudit(
                role,
                ACTION_SUBMIT_REVIEW,
                operatorId,
                operatorUsername,
                fromReviewStatus,
                role.getReviewStatus(),
                role.getStatus(),
                role.getStatus(),
                snapshot,
                null,
                null,
                reason
        );
        return result(role, ACTION_SUBMIT_REVIEW, audit, snapshot);
    }

    @Transactional(rollbackFor = Exception.class)
    public RoleGovernanceResult review(
            Long roleId,
            RoleReviewRequest request,
            Long operatorId,
            String operatorUsername,
            String operatorRoleCode
    ) {
        SysRole role = requireCustomForUpdate(roleId);
        if (!role.isPendingReview()) {
            throw stateError(ErrorKeys.ROLE_PACKAGE_NOT_PENDING_REVIEW, role);
        }
        requireSimpleTemplate(role);
        Snapshot snapshot = requireAssignablePermissions(
                role,
                rolePermissionRepository.findPermissionIdsByRoleId(roleId)
        );
        String reviewer = normalizeOperator(operatorUsername);
        if (Boolean.TRUE.equals(request.getApproved())
                && snapshot.highRiskCount() > 0
                && !SysRole.SUPER_ADMIN_ROLE_CODE.equals(operatorRoleCode)) {
            throw new BusinessException(ErrorKeys.ROLE_PACKAGE_SECOND_REVIEWER_REQUIRED, Map.of(
                    "roleId", roleId,
                    "roleCode", role.getRoleCode(),
                    "highRiskCount", snapshot.highRiskCount(),
                    "requiredReviewerRole", SysRole.SUPER_ADMIN_ROLE_CODE
            ));
        }
        if (Boolean.TRUE.equals(request.getApproved())
                && snapshot.highRiskCount() > 0
                && sameAccount(role, operatorId, reviewer)) {
            throw new BusinessException(ErrorKeys.ROLE_PACKAGE_SECOND_REVIEWER_REQUIRED, Map.of(
                    "roleId", roleId,
                    "roleCode", role.getRoleCode(),
                    "highRiskCount", snapshot.highRiskCount()
            ));
        }

        String comment = normalizeOptional(request.getComment());
        if (!Boolean.TRUE.equals(request.getApproved()) && comment == null) {
            throw new BusinessException(ErrorKeys.ROLE_PACKAGE_REJECTION_COMMENT_REQUIRED, Map.of(
                    "roleId", roleId,
                    "roleCode", role.getRoleCode()
            ));
        }

        String fromReviewStatus = role.getReviewStatus();
        String action;
        if (Boolean.TRUE.equals(request.getApproved())) {
            role.setReviewStatus(SysRole.REVIEW_STATUS_APPROVED);
            action = ACTION_APPROVE;
        } else {
            role.setReviewStatus(SysRole.REVIEW_STATUS_DRAFT);
            action = ACTION_REJECT;
        }
        role.setReviewedBy(operatorId);
        role.setReviewedByUsername(reviewer);
        role.setReviewedAt(LocalDateTime.now());
        role.setReviewComment(comment);
        roleRepository.save(role);

        SysRoleGovernanceAudit audit = saveAudit(
                role,
                action,
                operatorId,
                reviewer,
                fromReviewStatus,
                role.getReviewStatus(),
                role.getStatus(),
                role.getStatus(),
                snapshot,
                null,
                null,
                comment
        );
        return result(role, action, audit, snapshot);
    }

    /** Backward-compatible service entry used by existing internal callers and tests. */
    public RoleGovernanceResult review(
            Long roleId,
            RoleReviewRequest request,
            Long operatorId,
            String operatorUsername
    ) {
        return review(
                roleId,
                request,
                operatorId,
                operatorUsername,
                SysRole.SUPER_ADMIN_ROLE_CODE
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public RoleGovernanceResult activate(
            Long roleId,
            RoleRuntimeStatusRequest request,
            Long operatorId,
            String operatorUsername
    ) {
        SysRole role = requireCustomForUpdate(roleId);
        if (!role.isApproved()) {
            throw stateError(ErrorKeys.ROLE_PACKAGE_NOT_APPROVED, role);
        }
        if (role.isActive()) {
            throw stateError(ErrorKeys.ROLE_PACKAGE_ACTIVE_IMMUTABLE, role);
        }
        requireConfirmation(role, request);
        requireSimpleTemplate(role);
        Snapshot snapshot = requireAssignablePermissions(
                role,
                rolePermissionRepository.findPermissionIdsByRoleId(roleId)
        );

        String fromStatus = role.getStatus();
        role.setStatus("ACTIVE");
        roleRepository.save(role);
        SysRoleGovernanceAudit audit = saveAudit(
                role,
                ACTION_ACTIVATE,
                operatorId,
                operatorUsername,
                role.getReviewStatus(),
                role.getReviewStatus(),
                fromStatus,
                role.getStatus(),
                snapshot,
                null,
                null,
                request.getReason().trim()
        );
        evictRoleSecurity(roleId);
        return result(role, ACTION_ACTIVATE, audit, snapshot);
    }

    @Transactional(rollbackFor = Exception.class)
    public RoleGovernanceResult deactivate(
            Long roleId,
            RoleRuntimeStatusRequest request,
            Long operatorId,
            String operatorUsername
    ) {
        SysRole role = requireCustomForUpdate(roleId);
        if (!role.isActive()) {
            throw stateError(ErrorKeys.ROLE_DISABLED, role);
        }
        requireConfirmation(role, request);
        Snapshot snapshot = currentSnapshot(role);

        String fromStatus = role.getStatus();
        role.setStatus("DISABLED");
        roleRepository.save(role);
        SysRoleGovernanceAudit audit = saveAudit(
                role,
                ACTION_DEACTIVATE,
                operatorId,
                operatorUsername,
                role.getReviewStatus(),
                role.getReviewStatus(),
                fromStatus,
                role.getStatus(),
                snapshot,
                null,
                null,
                request.getReason().trim()
        );
        evictRoleSecurity(roleId);
        return result(role, ACTION_DEACTIVATE, audit, snapshot);
    }

    public List<RoleGovernanceAuditDTO> history(Long roleId) {
        roleRepository.findById(roleId).orElseThrow(() -> roleNotFound(roleId));
        return auditRepository.findByRoleIdOrderByCreatedAtDesc(roleId).stream()
                .map(this::toAuditDTO)
                .toList();
    }

    private SysRole requireCustomForUpdate(Long roleId) {
        SysRole role = roleRepository.findByIdForUpdate(roleId)
                .orElseThrow(() -> roleNotFound(roleId));
        if (role.isSystemRole() || role.isPrivilegedRole()) {
            throw new BusinessException(ErrorKeys.ROLE_PACKAGE_NOT_CUSTOM, Map.of(
                    "roleId", roleId,
                    "roleCode", role.getRoleCode()
            ));
        }
        return role;
    }

    private Snapshot requireAssignablePermissions(SysRole role, Set<Long> permissionIds) {
        Set<Long> requested = permissionIds == null ? Set.of() : new LinkedHashSet<>(permissionIds);
        List<SysPermission> permissions = new ArrayList<>(permissionRepository.findByIdIn(requested));
        if (permissions.size() != requested.size()) {
            Set<Long> found = permissions.stream().map(SysPermission::getId).collect(Collectors.toSet());
            Set<Long> missing = new LinkedHashSet<>(requested);
            missing.removeAll(found);
            throw new BusinessException(ErrorKeys.ROLE_PACKAGE_PERMISSION_NOT_ASSIGNABLE, Map.of(
                    "roleId", role.getId(),
                    "missingPermissionIds", missing
            ));
        }

        permissions.sort(Comparator.comparing(SysPermission::getPermissionCode));
        List<String> blocked = permissions.stream()
                .filter(permission -> !permission.isActive()
                        || !permission.isCustomAssignable()
                        || SysPermission.RISK_LEVEL_CRITICAL.equals(permission.getRiskLevel())
                        || SysPermission.isReservedPermissionCode(permission.getPermissionCode()))
                .map(SysPermission::getPermissionCode)
                .toList();
        if (!blocked.isEmpty()) {
            throw new BusinessException(ErrorKeys.ROLE_PACKAGE_PERMISSION_NOT_ASSIGNABLE, Map.of(
                    "roleId", role.getId(),
                    "permissionCodes", blocked
            ));
        }

        return snapshot(role, permissions);
    }

    /**
     * Runtime deactivation is an emergency access-revocation path. It must remain
     * available even when a permission in the historical snapshot has since been
     * retired or marked as no longer assignable.
     */
    private Snapshot currentSnapshot(SysRole role) {
        Set<Long> currentIds = rolePermissionRepository.findPermissionIdsByRoleId(role.getId());
        List<SysPermission> permissions = new ArrayList<>(permissionRepository.findByIdIn(currentIds));
        permissions.sort(Comparator.comparing(SysPermission::getPermissionCode));
        return snapshot(role, permissions);
    }

    private Snapshot snapshot(SysRole role, List<SysPermission> permissions) {
        int highRiskCount = (int) permissions.stream()
                .filter(permission -> SysPermission.RISK_LEVEL_HIGH.equals(permission.getRiskLevel()))
                .count();
        String permissionCodes = permissions.stream()
                .map(SysPermission::getPermissionCode)
                .collect(Collectors.joining(","));
        return new Snapshot(
                List.copyOf(permissions),
                highRiskCount,
                permissionCodes,
                fingerprint(role.getId(), permissions)
        );
    }

    private void requireSimpleTemplate(SysRole role) {
        SysApprovalTemplate template = approvalTemplateRepository
                .findByCompanyIdAndTemplateCode(role.getCompanyId(), role.getApprovalTemplateCode())
                .orElseThrow(() -> new BusinessException(ErrorKeys.APPROVAL_TEMPLATE_NOT_FOUND, Map.of(
                        "templateCode", Objects.toString(role.getApprovalTemplateCode(), "")
                )));
        if (!Boolean.TRUE.equals(template.getSystemDefined())
                || !"ACTIVE".equals(template.getStatus())
                || !"ROLE_PACKAGE".equals(template.getObjectType())
                || !"SIMPLE".equals(template.getApprovalMode())) {
            throw new BusinessException(ErrorKeys.APPROVAL_TEMPLATE_NOT_FOUND, Map.of(
                    "templateCode", template.getTemplateCode()
            ));
        }
    }

    private void requireConfirmation(SysRole role, RoleRuntimeStatusRequest request) {
        if (!role.getRoleCode().equalsIgnoreCase(request.getConfirmationCode().trim())) {
            throw new BusinessException(ErrorKeys.ROLE_PACKAGE_CONFIRMATION_REQUIRED, Map.of(
                    "roleId", role.getId(),
                    "requiredConfirmationCode", role.getRoleCode()
            ));
        }
    }

    private boolean sameAccount(SysRole role, Long operatorId, String operatorUsername) {
        boolean sameId = operatorId != null
                && operatorId > 0
                && operatorId.equals(role.getReviewSubmittedBy());
        boolean sameUsername = role.getReviewSubmittedByUsername() != null
                && role.getReviewSubmittedByUsername().equalsIgnoreCase(operatorUsername);
        return sameId || sameUsername;
    }

    private SysRoleGovernanceAudit saveAudit(
            SysRole role,
            String action,
            Long operatorId,
            String operatorUsername,
            String fromReviewStatus,
            String toReviewStatus,
            String fromRuntimeStatus,
            String toRuntimeStatus,
            Snapshot snapshot,
            String addedCodes,
            String removedCodes,
            String reason
    ) {
        return auditRepository.save(SysRoleGovernanceAudit.builder()
                .companyId(role.getCompanyId())
                .roleId(role.getId())
                .roleCode(role.getRoleCode())
                .action(action)
                .operatorId(operatorId)
                .operatorUsername(normalizeOperator(operatorUsername))
                .fromReviewStatus(fromReviewStatus)
                .toReviewStatus(toReviewStatus)
                .fromRuntimeStatus(fromRuntimeStatus)
                .toRuntimeStatus(toRuntimeStatus)
                .permissionCount(snapshot.permissions().size())
                .highRiskCount(snapshot.highRiskCount())
                .permissionCodes(emptyToNull(snapshot.permissionCodes()))
                .addedPermissionCodes(emptyToNull(addedCodes))
                .removedPermissionCodes(emptyToNull(removedCodes))
                .reason(reason)
                .snapshotFingerprint(snapshot.fingerprint())
                .build());
    }

    private RoleGovernanceResult result(
            SysRole role,
            String action,
            SysRoleGovernanceAudit audit,
            Snapshot snapshot
    ) {
        return RoleGovernanceResult.builder()
                .role(roleService.getRoleById(role.getId()))
                .action(action)
                .auditId(audit.getId())
                .permissionCount(snapshot.permissions().size())
                .highRiskCount(snapshot.highRiskCount())
                .snapshotFingerprint(snapshot.fingerprint())
                .build();
    }

    private RoleGovernanceAuditDTO toAuditDTO(SysRoleGovernanceAudit audit) {
        return RoleGovernanceAuditDTO.builder()
                .id(audit.getId())
                .action(audit.getAction())
                .operatorId(audit.getOperatorId())
                .operatorUsername(audit.getOperatorUsername())
                .fromReviewStatus(audit.getFromReviewStatus())
                .toReviewStatus(audit.getToReviewStatus())
                .fromRuntimeStatus(audit.getFromRuntimeStatus())
                .toRuntimeStatus(audit.getToRuntimeStatus())
                .permissionCount(audit.getPermissionCount())
                .highRiskCount(audit.getHighRiskCount())
                .permissionCodes(splitCodes(audit.getPermissionCodes()))
                .addedPermissionCodes(splitCodes(audit.getAddedPermissionCodes()))
                .removedPermissionCodes(splitCodes(audit.getRemovedPermissionCodes()))
                .reason(audit.getReason())
                .snapshotFingerprint(audit.getSnapshotFingerprint())
                .createdAt(audit.getCreatedAt())
                .build();
    }

    private String fingerprint(Long roleId, List<SysPermission> permissions) {
        String material = roleId + "|" + permissions.stream()
                .map(permission -> permission.getId() + ":" + permission.getPermissionCode()
                        + ":" + permission.getRiskLevel() + ":" + permission.isCustomAssignable())
                .collect(Collectors.joining("|"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private Set<Long> union(Set<Long> first, Set<Long> second) {
        Set<Long> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return result;
    }

    private String codesForIds(List<SysPermission> permissions, Set<Long> ids) {
        return permissions.stream()
                .filter(permission -> ids.contains(permission.getId()))
                .map(SysPermission::getPermissionCode)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private List<String> splitCodes(String codes) {
        if (codes == null || codes.isBlank()) {
            return List.of();
        }
        return List.of(codes.split(","));
    }

    private BusinessException stateError(String errorKey, SysRole role) {
        return new BusinessException(errorKey, Map.of(
                "roleId", role.getId(),
                "roleCode", role.getRoleCode(),
                "status", Objects.toString(role.getStatus(), ""),
                "reviewStatus", Objects.toString(role.getReviewStatus(), "")
        ));
    }

    private BusinessException roleNotFound(Long roleId) {
        return new BusinessException(ErrorKeys.ROLE_NOT_FOUND, Map.of("roleId", roleId));
    }

    private String normalizeOperator(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void evictRoleSecurity(Long roleId) {
        cacheService.onRolePermissionChanged(roleId);
        securityVersionService.bumpForRoleAndDescendants(roleId);
    }

    private record Snapshot(
            List<SysPermission> permissions,
            int highRiskCount,
            String permissionCodes,
            String fingerprint
    ) {
    }
}

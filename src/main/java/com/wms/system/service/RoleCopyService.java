package com.wms.system.service;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.dto.RoleCopyPreviewResponse;
import com.wms.system.dto.RoleCopyRequest;
import com.wms.system.dto.RoleCopyResult;
import com.wms.system.dto.RoleDTO;
import com.wms.system.entity.SysPermission;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysRoleCopyAudit;
import com.wms.system.entity.SysRolePermission;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysRoleCopyAuditRepository;
import com.wms.system.repository.SysRolePermissionRepository;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Creates independent custom roles from effective permission snapshots. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleCopyService {

    private static final String BLOCKER_CRITICAL_PERMISSION = "CRITICAL_PERMISSION_PRESENT";

    private final SysRoleRepository roleRepository;
    private final SysRolePermissionRepository rolePermissionRepository;
    private final SysRoleCopyAuditRepository auditRepository;
    private final DynamicPermissionService dynamicPermissionService;
    private final RoleService roleService;

    public RoleCopyPreviewResponse preview(Long sourceRoleId) {
        SysRole source = requireEligibleSource(sourceRoleId);
        return buildPreview(source);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public RoleCopyResult copy(
            Long sourceRoleId,
            RoleCopyRequest request,
            Long operatorId,
            String operatorUsername
    ) {
        SysRole source = requireEligibleSource(sourceRoleId);
        RoleCopyPreviewResponse currentPreview = buildPreview(source);

        if (!currentPreview.getSnapshotFingerprint().equalsIgnoreCase(request.getSnapshotFingerprint())) {
            throw new BusinessException(ErrorKeys.ROLE_COPY_SNAPSHOT_STALE, Map.of(
                    "sourceRoleId", sourceRoleId,
                    "currentFingerprint", currentPreview.getSnapshotFingerprint()
            ));
        }
        if (!Boolean.TRUE.equals(currentPreview.getCopyAllowed())) {
            throw new BusinessException(ErrorKeys.ROLE_COPY_CRITICAL_PERMISSION, Map.of(
                    "sourceRoleId", sourceRoleId,
                    "criticalRiskCount", currentPreview.getCriticalRiskCount()
            ));
        }

        String targetCode = normalizeRoleCode(request.getRoleCode());
        validateHighRiskConfirmation(request, targetCode, currentPreview.getHighRiskCount());

        if (roleRepository.existsByCompanyIdAndRoleCode(
                source.getCompanyId(), targetCode)) {
            throw new BusinessException(ErrorKeys.ROLE_COPY_TARGET_EXISTS, Map.of(
                    "roleCode", targetCode
            ));
        }

        SysRole target = SysRole.builder()
                .roleCode(targetCode)
                .roleName(request.getRoleName().trim())
                .description(request.getDescription().trim())
                .roleType(SysRole.ROLE_TYPE_CUSTOM)
                .systemCategory(null)
                .importAllowed(true)
                .approvalTemplateCode(SysRole.SIMPLE_APPROVAL_TEMPLATE_CODE)
                .reviewStatus(SysRole.REVIEW_STATUS_DRAFT)
                .status("DISABLED")
                .sortOrder(source.getSortOrder() == null ? 1000 : source.getSortOrder() + 1)
                .build();
        target.setCompanyId(source.getCompanyId());

        final SysRole savedTarget;
        try {
            savedTarget = roleRepository.saveAndFlush(target);
        } catch (DataIntegrityViolationException duplicate) {
            throw new BusinessException(ErrorKeys.ROLE_COPY_TARGET_EXISTS, Map.of(
                    "roleCode", targetCode
            ));
        }

        List<SysRolePermission> assignments = currentPreview.getPermissions().stream()
                .map(permission -> SysRolePermission.builder()
                        .companyId(savedTarget.getCompanyId())
                        .roleId(savedTarget.getId())
                        .permissionId(permission.getId())
                        .grantedBy(operatorId)
                        .build())
                .toList();
        rolePermissionRepository.saveAll(assignments);

        String highRiskCodes = currentPreview.getHighRiskPermissions().stream()
                .map(PermissionDTO::getPermissionCode)
                .collect(Collectors.joining(","));
        SysRoleCopyAudit audit = auditRepository.save(SysRoleCopyAudit.builder()
                .companyId(savedTarget.getCompanyId())
                .operatorId(operatorId)
                .operatorUsername(normalizeOperator(operatorUsername))
                .sourceRoleId(source.getId())
                .sourceRoleCode(source.getRoleCode())
                .targetRoleId(savedTarget.getId())
                .targetRoleCode(savedTarget.getRoleCode())
                .permissionCount(currentPreview.getPermissionCount())
                .highRiskCount(currentPreview.getHighRiskCount())
                .highRiskPermissionCodes(highRiskCodes.isEmpty() ? null : highRiskCodes)
                .operationReason(normalizeOptional(request.getOperationReason()))
                .snapshotFingerprint(currentPreview.getSnapshotFingerprint())
                .build());

        log.info("Role permission snapshot copied: source={}, target={}, permissions={}, highRisk={}, operator={}",
                source.getRoleCode(), savedTarget.getRoleCode(), currentPreview.getPermissionCount(),
                currentPreview.getHighRiskCount(), normalizeOperator(operatorUsername));

        RoleDTO targetDto = roleService.getRoleById(savedTarget.getId());
        return RoleCopyResult.builder()
                .role(targetDto)
                .sourceRoleId(source.getId())
                .sourceRoleCode(source.getRoleCode())
                .permissionCount(currentPreview.getPermissionCount())
                .highRiskCount(currentPreview.getHighRiskCount())
                .snapshotFingerprint(currentPreview.getSnapshotFingerprint())
                .auditId(audit.getId())
                .build();
    }

    private SysRole requireEligibleSource(Long sourceRoleId) {
        SysRole source = roleRepository.findByCompanyIdAndId(
                CompanyScope.currentCompanyId(), sourceRoleId)
                .orElseThrow(() -> new BusinessException(ErrorKeys.ROLE_NOT_FOUND, Map.of(
                        "roleId", sourceRoleId
                )));
        if (!source.isActive()) {
            throw new BusinessException(ErrorKeys.ROLE_COPY_SOURCE_DISABLED, Map.of(
                    "roleId", sourceRoleId,
                    "roleCode", source.getRoleCode()
            ));
        }
        if (!source.canImportPermissions()) {
            throw new BusinessException(ErrorKeys.ROLE_COPY_SOURCE_NOT_ALLOWED, Map.of(
                    "roleId", sourceRoleId,
                    "roleCode", source.getRoleCode()
            ));
        }
        return source;
    }

    private RoleCopyPreviewResponse buildPreview(SysRole source) {
        Set<Long> effectiveRoleIds = dynamicPermissionService.getInheritedRoleIds(Set.of(source.getId()));
        List<PermissionDTO> permissions = new ArrayList<>(
                dynamicPermissionService.getPermissionsByRoleIds(effectiveRoleIds));
        permissions.sort(Comparator.comparing(
                PermissionDTO::getPermissionCode,
                Comparator.nullsLast(String::compareTo)
        ));

        List<PermissionDTO> highRisk = permissions.stream()
                .filter(permission -> SysPermission.RISK_LEVEL_HIGH.equals(permission.getRiskLevel()))
                .toList();
        int criticalCount = (int) permissions.stream()
                .filter(this::isCriticalOrBlocked)
                .count();
        int normalCount = (int) permissions.stream()
                .filter(permission -> SysPermission.RISK_LEVEL_NORMAL.equals(permission.getRiskLevel()))
                .count();

        String fingerprint = fingerprint(source.getId(), permissions);
        boolean copyAllowed = criticalCount == 0;
        return RoleCopyPreviewResponse.builder()
                .sourceRoleId(source.getId())
                .sourceRoleCode(source.getRoleCode())
                .sourceRoleName(source.getRoleName())
                .sourceRoleType(source.getRoleType())
                .sourceSystemCategory(source.getSystemCategory())
                .permissionCount(permissions.size())
                .normalRiskCount(normalCount)
                .highRiskCount(highRisk.size())
                .criticalRiskCount(criticalCount)
                .permissions(List.copyOf(permissions))
                .highRiskPermissions(highRisk)
                .snapshotFingerprint(fingerprint)
                .copyAllowed(copyAllowed)
                .blockers(copyAllowed ? List.of() : List.of(BLOCKER_CRITICAL_PERMISSION))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private boolean isCriticalOrBlocked(PermissionDTO permission) {
        return SysPermission.RISK_LEVEL_CRITICAL.equals(permission.getRiskLevel())
                || !Boolean.TRUE.equals(permission.getCustomAssignable())
                || SysPermission.isReservedPermissionCode(permission.getPermissionCode());
    }

    private void validateHighRiskConfirmation(
            RoleCopyRequest request,
            String targetCode,
            Integer highRiskCount
    ) {
        if (highRiskCount == null || highRiskCount == 0) {
            return;
        }

        boolean acknowledged = Boolean.TRUE.equals(request.getRiskAcknowledged());
        boolean codeConfirmed = targetCode.equals(normalizeRoleCode(request.getConfirmationCode()));
        boolean reasonPresent = request.getOperationReason() != null
                && !request.getOperationReason().isBlank();
        if (!acknowledged || !codeConfirmed || !reasonPresent) {
            throw new BusinessException(ErrorKeys.ROLE_COPY_RISK_CONFIRMATION_REQUIRED, Map.of(
                    "highRiskCount", highRiskCount,
                    "requiredConfirmationCode", targetCode
            ));
        }
    }

    private String fingerprint(Long sourceRoleId, List<PermissionDTO> permissions) {
        StringBuilder material = new StringBuilder().append(sourceRoleId).append('|');
        for (PermissionDTO permission : permissions) {
            material.append(permission.getId()).append(':')
                    .append(permission.getPermissionCode()).append(':')
                    .append(permission.getRiskLevel()).append(':')
                    .append(Boolean.TRUE.equals(permission.getCustomAssignable()))
                    .append('|');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    material.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String normalizeRoleCode(String roleCode) {
        return roleCode == null ? "" : roleCode.trim().toUpperCase();
    }

    private String normalizeOperator(String operatorUsername) {
        return operatorUsername == null || operatorUsername.isBlank()
                ? "unknown"
                : operatorUsername.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

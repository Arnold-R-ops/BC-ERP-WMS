package com.wms.system.service;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.entity.SysPermission;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysRolePermission;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysPermissionRepository;
import com.wms.system.repository.SysRolePermissionRepository;
import com.wms.system.repository.SysRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map;

/**
 * Role-Permission Assignment Service
 *
 * Manages many-to-many relationships between roles and permissions.
 *
 * Key Features:
 * - Assign permissions to roles
 * - Remove permissions from roles
 * - Query role's permissions
 * - Query permission's roles
 * - Batch operations
 * - Cache invalidation after changes
 *
 * Business Rules:
 * - One role can have multiple permissions
 * - Permission assignment triggers cache eviction for all users with that role
 * - Cannot assign same permission twice to same role
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RolePermissionService {

    private final SysRolePermissionRepository rolePermissionRepository;
    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;
    private final PermissionCacheService cacheService;
    private final SecurityVersionService securityVersionService;

    /**
     * Assign permission to role
     *
     * @param roleId Role ID
     * @param permissionId Permission ID
     * @param grantedBy Administrator ID who made the grant
     * @throws IllegalArgumentException if role or permission not found
     */
    @Transactional
    public void assignPermissionToRole(Long roleId, Long permissionId, Long grantedBy) {
        log.info("Assigning permission {} to role {} by admin {}", permissionId, roleId, grantedBy);

        // Validate role exists
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));
        validateRoleEditable(role);

        // Validate permission exists
        SysPermission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permissionId));

        validateCustomRoleAssignment(role, permission);

        // Check if already assigned
        if (rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)) {
            log.warn("Permission {} already assigned to role {}", permissionId, roleId);
            return;
        }

        // Create assignment
        SysRolePermission rolePermission = SysRolePermission.builder()
                .roleId(roleId)
                .permissionId(permissionId)
                .grantedBy(grantedBy)
                .build();

        rolePermissionRepository.save(rolePermission);

        // Evict cache for all users with this role
        cacheService.onRolePermissionChanged(roleId);
        securityVersionService.bumpForRoleAndDescendants(roleId);

        log.info("Permission assigned successfully: permission={}, role={}", permissionId, roleId);
    }

    /**
     * Remove permission from role
     *
     * @param roleId Role ID
     * @param permissionId Permission ID
     */
    @Transactional
    public void removePermissionFromRole(Long roleId, Long permissionId) {
        log.info("Removing permission {} from role {}", permissionId, roleId);

        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));
        validateRoleEditable(role);

        rolePermissionRepository.deleteByRoleIdAndPermissionId(roleId, permissionId);

        // Evict cache for all users with this role
        cacheService.onRolePermissionChanged(roleId);
        securityVersionService.bumpForRoleAndDescendants(roleId);

        log.info("Permission removed successfully: permission={}, role={}", permissionId, roleId);
    }

    /**
     * Batch assign permissions to role
     *
     * Replaces all existing permissions with new set.
     *
     * @param roleId Role ID
     * @param permissionIds Set of permission IDs
     * @param grantedBy Administrator ID
     */
    @Transactional
    public void assignPermissionsToRole(Long roleId, Set<Long> permissionIds, Long grantedBy) {
        log.info("Batch assigning {} permissions to role {}", permissionIds.size(), roleId);

        // Validate role exists
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));
        validateRoleEditable(role);

        List<SysPermission> permissions = new ArrayList<>(permissionRepository.findByIdIn(permissionIds));
        if (permissions.size() != permissionIds.size()) {
            Set<Long> foundIds = permissions.stream().map(SysPermission::getId).collect(Collectors.toSet());
            Set<Long> missingIds = permissionIds.stream()
                    .filter(permissionId -> !foundIds.contains(permissionId))
                    .collect(Collectors.toSet());
            throw new BusinessException(ErrorKeys.ROLE_PACKAGE_PERMISSION_NOT_ASSIGNABLE, Map.of(
                    "roleId", roleId,
                    "missingPermissionIds", missingIds
            ));
        }
        permissions.forEach(permission -> validateCustomRoleAssignment(role, permission));

        // Validate the complete replacement before deleting existing rows.
        rolePermissionRepository.deleteByRoleId(roleId);

        // Assign new permissions
        for (SysPermission permission : permissions) {

            SysRolePermission rolePermission = SysRolePermission.builder()
                    .roleId(roleId)
                    .permissionId(permission.getId())
                    .grantedBy(grantedBy)
                    .build();

            rolePermissionRepository.save(rolePermission);
        }

        // Evict cache for all users with this role
        cacheService.onRolePermissionChanged(roleId);
        securityVersionService.bumpForRoleAndDescendants(roleId);

        log.info("Batch permission assignment completed: {} permissions assigned to role {}",
                permissions.size(), roleId);
    }

    /**
     * Get permission IDs for a role
     *
     * @param roleId Role ID
     * @return Set of permission IDs
     */
    public Set<Long> getRolePermissionIds(Long roleId) {
        return rolePermissionRepository.findPermissionIdsByRoleId(roleId);
    }

    /**
     * Get permissions for a role (with details)
     *
     * @param roleId Role ID
     * @return List of permissions
     */
    public List<PermissionDTO> getRolePermissions(Long roleId) {
        Set<Long> permissionIds = rolePermissionRepository.findPermissionIdsByRoleId(roleId);
        if (permissionIds.isEmpty()) {
            return List.of();
        }

        return permissionRepository.findByIdIn(permissionIds).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get role IDs for a permission
     *
     * @param permissionId Permission ID
     * @return Set of role IDs
     */
    public Set<Long> getPermissionRoleIds(Long permissionId) {
        return rolePermissionRepository.findRoleIdsByPermissionId(permissionId);
    }

    /**
     * Check if role has specific permission
     *
     * @param roleId Role ID
     * @param permissionId Permission ID
     * @return true if role has the permission
     */
    public boolean roleHasPermission(Long roleId, Long permissionId) {
        return rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId);
    }

    /**
     * Get permission count for a role
     *
     * @param roleId Role ID
     * @return Number of permissions for this role
     */
    public long getPermissionCountForRole(Long roleId) {
        return rolePermissionRepository.countByRoleId(roleId);
    }

    // ========== Helper Methods ==========

    /**
     * Convert SysPermission entity to PermissionDTO
     *
     * @param permission SysPermission entity
     * @return PermissionDTO
     */
    private PermissionDTO convertToDTO(SysPermission permission) {
        return PermissionDTO.builder()
                .id(permission.getId())
                .permissionCode(permission.getPermissionCode())
                .permissionName(permission.getPermissionName())
                .permissionType(permission.getPermissionType())
                .parentId(permission.getParentId())
                .resourcePath(permission.getResourcePath())
                .httpMethod(permission.getHttpMethod())
                .menuUrl(permission.getMenuUrl())
                .menuIcon(permission.getMenuIcon())
                .dataScope(permission.getDataScope())
                .riskLevel(permission.getRiskLevel())
                .customAssignable(permission.isCustomAssignable())
                .description(permission.getDescription())
                .status(permission.getStatus())
                .sortOrder(permission.getSortOrder())
                .build();
    }

    private void validateCustomRoleAssignment(SysRole role, SysPermission permission) {
        if (!permission.isActive()
                || !permission.isCustomAssignable()
                || SysPermission.RISK_LEVEL_CRITICAL.equals(permission.getRiskLevel())
                || SysPermission.isReservedPermissionCode(permission.getPermissionCode())) {
            throw new BusinessException(ErrorKeys.ROLE_PACKAGE_PERMISSION_NOT_ASSIGNABLE, Map.of(
                    "roleId", role.getId(),
                    "permissionCode", permission.getPermissionCode()
            ));
        }
    }

    private void validateRoleEditable(SysRole role) {
        if (role.isSystemRole() || role.isPrivilegedRole()) {
            throw new BusinessException(ErrorKeys.ROLE_PACKAGE_NOT_CUSTOM, Map.of(
                    "roleId", role.getId(),
                    "roleCode", role.getRoleCode()
            ));
        }
        if (role.isActive()) {
            throw new BusinessException(ErrorKeys.ROLE_PACKAGE_ACTIVE_IMMUTABLE, Map.of(
                    "roleId", role.getId(),
                    "roleCode", role.getRoleCode()
            ));
        }
        if (!role.isDraft()) {
            throw new BusinessException(ErrorKeys.ROLE_PACKAGE_NOT_DRAFT, Map.of(
                    "roleId", role.getId(),
                    "roleCode", role.getRoleCode(),
                    "reviewStatus", role.getReviewStatus()
            ));
        }
    }
}

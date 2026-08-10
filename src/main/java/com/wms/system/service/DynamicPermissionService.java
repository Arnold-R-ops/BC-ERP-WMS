package com.wms.system.service;

import com.wms.system.config.CacheConfig;
import com.wms.system.dto.PermissionDTO;
import com.wms.system.dto.UserPermissionDTO;
import com.wms.system.entity.SysPermission;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysRolePermission;
import com.wms.system.entity.SysUserRole;
import com.wms.system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dynamic Permission Service
 *
 * Core service for dynamic RBAC permission system.
 * Handles role inheritance and permission resolution.
 *
 * Key Features:
 * - Recursive role inheritance resolution
 * - Permission deduplication
 * - Caffeine cache for performance
 * - Support for multi-level role hierarchy
 *
 * Permission Resolution Flow:
 * 1. Get user's direct roles (from sys_user_role)
 * 2. Recursively get inherited roles (from sys_role_inherit)
 * 3. Collect all permissions from all roles (from sys_role_permission)
 * 4. Deduplicate and return permissions
 * 5. Cache result for 30 minutes
 *
 * Example: CHAIRMAN Role
 * - Direct roles: [CHAIRMAN]
 * - Inherited roles: [WAREHOUSE_ADMIN, BUYER, SELLER]
 * - Effective roles: [CHAIRMAN, WAREHOUSE_ADMIN, BUYER, SELLER]
 * - Permissions: Union of all 4 roles' permissions
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DynamicPermissionService {

    private final SysUserRoleRepository userRoleRepository;
    private final SysRoleRepository roleRepository;
    private final SysRoleInheritRepository roleInheritRepository;
    private final SysRolePermissionRepository rolePermissionRepository;
    private final SysPermissionRepository permissionRepository;

    /**
     * Get user's complete permission set (with caching)
     *
     * This is the main entry point for permission loading.
     * Results are cached for 30 minutes to improve performance.
     *
     * @param userId User ID
     * @return UserPermissionDTO with all permissions
     */
    @Cacheable(value = CacheConfig.USER_PERMISSIONS_CACHE, key = "#userId")
    public UserPermissionDTO getUserPermissions(Long userId) {
        log.info("Loading permissions for user ID: {} (cache miss)", userId);

        // 1. Get user's enabled direct role IDs. Disabled roles never
        // contribute permissions, even through the legacy all-role resolver.
        Set<Long> assignedRoleIds = userRoleRepository.findRoleIdsByUserId(userId);
        if (assignedRoleIds.isEmpty()) {
            log.warn("User {} has no roles assigned", userId);
            return buildEmptyPermissionDTO(userId);
        }
        Set<Long> directRoleIds = findActiveRoleIds(assignedRoleIds);
        if (directRoleIds.isEmpty()) {
            log.warn("User {} has no active roles assigned", userId);
            return buildEmptyPermissionDTO(userId);
        }

        // 2. Recursively get all inherited role IDs
        Set<Long> allRoleIds = getInheritedRoleIds(directRoleIds);
        log.debug("User {} has {} direct roles, {} effective roles (including inherited)",
                userId, directRoleIds.size(), allRoleIds.size());

        // 3. Get all permissions from all roles
        List<PermissionDTO> allPermissions = getPermissionsByRoleIds(allRoleIds);
        log.debug("User {} has {} total permissions", userId, allPermissions.size());

        // 4. Build UserPermissionDTO
        return buildUserPermissionDTO(userId, directRoleIds, allRoleIds, allPermissions);
    }

    /**
     * Resolve permissions strictly for the role currently activated in the JWT.
     *
     * The assignment and role status are checked against live database state.
     * Other roles assigned to the same user are deliberately excluded.
     *
     * @param userId user whose assignment must be verified
     * @param roleCode role selected in the current JWT
     * @param securityVersion live user security version, included in the cache key
     * @return permissions of the selected role and its inherited parents only
     */
    @Cacheable(
            value = CacheConfig.USER_PERMISSIONS_CACHE,
            key = "'active:' + #userId + ':' + #roleCode + ':' + #securityVersion"
    )
    public UserPermissionDTO getUserPermissionsForRole(
            Long userId,
            String roleCode,
            Long securityVersion
    ) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new IllegalArgumentException("Current role is required");
        }

        SysRole selectedRole = roleRepository.findByRoleCode(roleCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Current role does not exist: " + roleCode));

        if (!selectedRole.isActive()) {
            throw new IllegalArgumentException("Current role is disabled: " + roleCode);
        }

        if (!userRoleRepository.existsByUserIdAndRoleId(userId, selectedRole.getId())) {
            throw new IllegalArgumentException(
                    "Current role is not assigned to user: " + roleCode);
        }

        Set<Long> directRoleIds = Set.of(selectedRole.getId());
        Set<Long> effectiveRoleIds = getInheritedRoleIds(directRoleIds);
        List<PermissionDTO> permissions = getPermissionsByRoleIds(effectiveRoleIds);

        log.debug("Resolved active-role permissions: userId={}, roleCode={}, " +
                        "securityVersion={}, effectiveRoles={}, permissions={}",
                userId, roleCode, securityVersion, effectiveRoleIds.size(), permissions.size());

        return buildUserPermissionDTO(
                userId,
                directRoleIds,
                effectiveRoleIds,
                permissions
        );
    }

    /**
     * Recursively get all role IDs (including inherited roles)
     *
     * Supports multi-level inheritance:
     * - If A inherits B, and B inherits C, returns [A, B, C]
     *
     * Algorithm:
     * 1. Start with direct role IDs
     * 2. For each role, query parent roles from sys_role_inherit
     * 3. Exclude disabled parents and stop traversal through those branches
     * 4. Recursively query active parents' parents
     * 5. Return deduplicated set of all active effective role IDs
     *
     * Performance:
     * - Uses iterative approach to avoid stack overflow
     * - Caches intermediate results
     * - Maximum depth: 10 levels (to prevent infinite loops)
     *
     * @param roleIds Starting role IDs (user's direct roles)
     * @return All role IDs (direct + inherited)
     */
    @Cacheable(value = CacheConfig.ROLE_INHERIT_CACHE, key = "#roleIds")
    public Set<Long> getInheritedRoleIds(Set<Long> roleIds) {
        Set<Long> result = new HashSet<>(roleIds);
        Set<Long> currentLevel = new HashSet<>(roleIds);
        int depth = 0;
        int maxDepth = 10; // Prevent infinite loops

        while (!currentLevel.isEmpty() && depth < maxDepth) {
            Set<Long> candidateParentRoleIds = new HashSet<>();

            // Query parent roles for current level
            for (Long roleId : currentLevel) {
                Set<Long> parentRoleIds = roleInheritRepository.findParentRoleIdsByChildRoleId(roleId);
                for (Long parentRoleId : parentRoleIds) {
                    if (!result.contains(parentRoleId)) {
                        candidateParentRoleIds.add(parentRoleId);
                    }
                }
            }

            // A disabled parent contributes no permissions and terminates that
            // inheritance branch, so only active parents enter the next level.
            Set<Long> nextLevel = findActiveRoleIds(candidateParentRoleIds);
            result.addAll(nextLevel);
            currentLevel = nextLevel;
            depth++;
        }

        if (depth >= maxDepth) {
            log.warn("Role inheritance depth exceeded maximum ({}). Possible circular inheritance.", maxDepth);
        }

        return result;
    }

    private Set<Long> findActiveRoleIds(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Collections.emptySet();
        }

        return roleRepository.findByIdIn(roleIds).stream()
                .filter(SysRole::isActive)
                .map(SysRole::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Get all permissions for a set of role IDs
     *
     * Queries sys_role_permission and sys_permission tables.
     * Filters out DISABLED permissions.
     * Deduplicates permissions by ID.
     *
     * @param roleIds Set of role IDs (including inherited roles)
     * @return List of permission DTOs
     */
    public List<PermissionDTO> getPermissionsByRoleIds(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Query role-permission associations with permission details
        List<SysRolePermission> rolePermissions = rolePermissionRepository.findByRoleIdInWithPermission(roleIds);

        // Extract unique permissions (deduplicate by ID)
        Map<Long, SysPermission> permissionMap = new HashMap<>();
        for (SysRolePermission rp : rolePermissions) {
            SysPermission permission = rp.getPermission();
            SysRole grantingRole = rp.getRole();
            boolean allowedForRole = permission != null
                    && (permission.isCustomAssignable()
                    || (grantingRole != null && grantingRole.isSystemRole()));
            if (permission != null && permission.isActive() && allowedForRole) {
                permissionMap.put(permission.getId(), permission);
            }
        }

        // Convert to DTOs
        return permissionMap.values().stream()
                .map(this::convertToDTO)
                .sorted(Comparator.comparing(PermissionDTO::getSortOrder))
                .collect(Collectors.toList());
    }

    /**
     * Build UserPermissionDTO from permission list
     *
     * Categorizes permissions by type:
     * - MENU permissions
     * - API permissions
     * - BUTTON permissions
     *
     * Extracts permission codes for quick lookup.
     * Extracts role codes for role-based checks.
     *
     * @param userId User ID
     * @param directRoleIds User's direct role IDs
     * @param allRoleIds All effective role IDs (including inherited)
     * @param permissions All permissions
     * @return UserPermissionDTO
     */
    private UserPermissionDTO buildUserPermissionDTO(Long userId,
                                                     Set<Long> directRoleIds,
                                                     Set<Long> allRoleIds,
                                                     List<PermissionDTO> permissions) {
        // Get role codes
        List<SysRole> roles = roleRepository.findByIdIn(directRoleIds);
        Set<String> roleCodes = roles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toSet());

        // Categorize permissions by type
        List<PermissionDTO> menuPermissions = permissions.stream()
                .filter(PermissionDTO::isMenuPermission)
                .collect(Collectors.toList());

        List<PermissionDTO> apiPermissions = permissions.stream()
                .filter(PermissionDTO::isApiPermission)
                .collect(Collectors.toList());

        List<PermissionDTO> buttonPermissions = permissions.stream()
                .filter(PermissionDTO::isButtonPermission)
                .collect(Collectors.toList());

        // Extract permission codes
        Set<String> permissionCodes = permissions.stream()
                .map(PermissionDTO::getPermissionCode)
                .collect(Collectors.toSet());

        return UserPermissionDTO.builder()
                .userId(userId)
                .roleIds(directRoleIds)
                .roleCodes(roleCodes)
                .effectiveRoleIds(allRoleIds)
                .permissions(permissions)
                .menuPermissions(menuPermissions)
                .apiPermissions(apiPermissions)
                .buttonPermissions(buttonPermissions)
                .permissionCodes(permissionCodes)
                .build();
    }

    /**
     * Build empty permission DTO (for users with no roles)
     *
     * @param userId User ID
     * @return Empty UserPermissionDTO
     */
    private UserPermissionDTO buildEmptyPermissionDTO(Long userId) {
        return UserPermissionDTO.builder()
                .userId(userId)
                .roleIds(Collections.emptySet())
                .roleCodes(Collections.emptySet())
                .effectiveRoleIds(Collections.emptySet())
                .permissions(Collections.emptyList())
                .menuPermissions(Collections.emptyList())
                .apiPermissions(Collections.emptyList())
                .buttonPermissions(Collections.emptyList())
                .permissionCodes(Collections.emptySet())
                .build();
    }

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

    /**
     * Check if user has specific permission
     *
     * @param userId User ID
     * @param permissionCode Permission code
     * @return true if user has the permission
     */
    public boolean hasPermission(Long userId, String permissionCode) {
        UserPermissionDTO userPermissions = getUserPermissions(userId);
        return userPermissions.hasPermission(permissionCode);
    }

    /**
     * Check if user has any of the specified permissions
     *
     * @param userId User ID
     * @param permissionCodes Permission codes
     * @return true if user has any permission
     */
    public boolean hasAnyPermission(Long userId, String... permissionCodes) {
        UserPermissionDTO userPermissions = getUserPermissions(userId);
        return userPermissions.hasAnyPermission(permissionCodes);
    }

    /**
     * Check if user has all of the specified permissions
     *
     * @param userId User ID
     * @param permissionCodes Permission codes
     * @return true if user has all permissions
     */
    public boolean hasAllPermissions(Long userId, String... permissionCodes) {
        UserPermissionDTO userPermissions = getUserPermissions(userId);
        return userPermissions.hasAllPermissions(permissionCodes);
    }
}

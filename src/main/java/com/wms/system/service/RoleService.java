package com.wms.system.service;

import com.wms.system.dto.RoleDTO;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysRoleInherit;
import com.wms.system.repository.SysRoleInheritRepository;
import com.wms.system.repository.SysRolePermissionRepository;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Role Management Service
 *
 * Provides CRUD operations for system roles.
 * Manages role inheritance relationships.
 *
 * Key Features:
 * - Create, update, delete roles
 * - Query roles by various criteria
 * - Manage role inheritance (parent-child relationships)
 * - Validate role operations (prevent deleting system roles)
 * - Cache invalidation after role changes
 *
 * Business Rules:
 * - SYSTEM roles cannot be deleted (e.g., TENANT_ADMIN, CHAIRMAN)
 * - Role codes must be unique
 * - Cannot create circular inheritance
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private final SysRoleRepository roleRepository;
    private final SysRoleInheritRepository roleInheritRepository;
    private final SysRolePermissionRepository rolePermissionRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final PermissionCacheService cacheService;
    private final SecurityVersionService securityVersionService;

    /**
     * Create a new role
     *
     * @param roleDTO Role data
     * @return Created role DTO
     * @throws IllegalArgumentException if role code already exists
     */
    @Transactional
    public RoleDTO createRole(RoleDTO roleDTO) {
        Long companyId = CompanyScope.currentCompanyId();
        log.info("Creating new role: {}", roleDTO.getRoleCode());

        // Validate role code uniqueness
        if (roleRepository.existsByCompanyIdAndRoleCode(companyId, roleDTO.getRoleCode())) {
            throw new IllegalArgumentException("Role code already exists: " + roleDTO.getRoleCode());
        }

        // Create role entity
        SysRole role = SysRole.builder()
                .roleCode(roleDTO.getRoleCode())
                .roleName(roleDTO.getRoleName())
                .description(roleDTO.getDescription())
                // Generic creation can only create custom roles. System roles are
                // deployment-owned records created through versioned migrations.
                .roleType(SysRole.ROLE_TYPE_CUSTOM)
                .systemCategory(null)
                .importAllowed(true)
                .approvalTemplateCode(SysRole.SIMPLE_APPROVAL_TEMPLATE_CODE)
                .reviewStatus(SysRole.REVIEW_STATUS_DRAFT)
                .status("DISABLED")
                .sortOrder(roleDTO.getSortOrder() != null ? roleDTO.getSortOrder() : 0)
                .build();

        // Save role
        role.setCompanyId(companyId);
        role = roleRepository.save(role);

        log.info("Role created successfully: {} (ID: {})", role.getRoleCode(), role.getId());

        return convertToDTO(role);
    }

    /**
     * Update an existing role
     *
     * @param roleId Role ID
     * @param roleDTO Updated role data
     * @return Updated role DTO
     * @throws IllegalArgumentException if role not found or cannot be updated
     */
    @Transactional
    public RoleDTO updateRole(Long roleId, RoleDTO roleDTO) {
        Long companyId = CompanyScope.currentCompanyId();
        log.info("Updating role ID: {}", roleId);

        // Find existing role
        SysRole role = roleRepository.findByCompanyIdAndId(companyId, roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));

        if (role.isSystemRole() || role.isPrivilegedRole()) {
            throw new IllegalArgumentException("System roles cannot be modified online");
        }
        if (role.isActive() || !role.isDraft()) {
            throw new IllegalArgumentException("Only disabled custom drafts can be modified");
        }

        // Update fields
        role.setRoleName(roleDTO.getRoleName());
        role.setDescription(roleDTO.getDescription());
        role.setSortOrder(roleDTO.getSortOrder());

        // Save changes
        role = roleRepository.save(role);

        // Invalidate cache for users with this role
        cacheService.evictPermissionsForRole(roleId);
        cacheService.evictRoleInheritCache();
        securityVersionService.bumpForRoleAndDescendants(roleId);

        log.info("Role updated successfully: {} (ID: {})", role.getRoleCode(), role.getId());

        return convertToDTO(role);
    }

    /**
     * Delete a role
     *
     * @param roleId Role ID
     * @throws IllegalArgumentException if role is system role or has users assigned
     */
    @Transactional
    public void deleteRole(Long roleId) {
        Long companyId = CompanyScope.currentCompanyId();
        log.info("Deleting role ID: {}", roleId);

        // Find role
        SysRole role = roleRepository.findByCompanyIdAndId(companyId, roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));

        // Prevent deleting system roles
        if (role.isSystemRole()) {
            throw new IllegalArgumentException("Cannot delete system role: " + role.getRoleCode());
        }

        // Check if role has users assigned
        long userCount = userRoleRepository.countByCompanyIdAndRoleId(companyId, roleId);
        if (userCount > 0) {
            throw new IllegalArgumentException(
                    String.format("Cannot delete role with %d users assigned", userCount));
        }

        // Revoke tokens for users assigned to descendant roles while the
        // inheritance graph still contains this role. Deleting first would
        // cascade the inheritance rows and make those users undiscoverable.
        securityVersionService.bumpForRoleAndDescendants(roleId);

        // Delete role (cascade will delete associations)
        roleRepository.delete(role);

        // Invalidate cache
        cacheService.evictCompanyUserPermissions(companyId);
        cacheService.evictRoleInheritCache();

        log.info("Role deleted successfully: {} (ID: {})", role.getRoleCode(), role.getId());
    }

    /**
     * Get role by ID
     *
     * @param roleId Role ID
     * @return Role DTO
     */
    public RoleDTO getRoleById(Long roleId) {
        SysRole role = roleRepository.findByCompanyIdAndId(
                CompanyScope.currentCompanyId(), roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));

        return convertToDTO(role);
    }

    /**
     * Get role by role code
     *
     * @param roleCode Role code
     * @return Role DTO
     */
    public RoleDTO getRoleByCode(String roleCode) {
        SysRole role = roleRepository.findByCompanyIdAndRoleCode(
                CompanyScope.currentCompanyId(), roleCode)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleCode));

        return convertToDTO(role);
    }

    /**
     * Get all roles
     *
     * @return List of role DTOs
     */
    public List<RoleDTO> getAllRoles() {
        return roleRepository.findByCompanyIdOrderBySortOrderAsc(
                CompanyScope.currentCompanyId()).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all active roles
     *
     * @return List of active role DTOs
     */
    public List<RoleDTO> getAllActiveRoles() {
        return roleRepository.findByCompanyIdAndStatus(
                CompanyScope.currentCompanyId(), "ACTIVE").stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get system predefined roles
     *
     * @return List of system role DTOs
     */
    public List<RoleDTO> getSystemRoles() {
        return roleRepository.findByCompanyIdAndRoleType(
                CompanyScope.currentCompanyId(), "SYSTEM").stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Search roles by name
     *
     * @param keyword Search keyword
     * @return List of matching role DTOs
     */
    public List<RoleDTO> searchRoles(String keyword) {
        return roleRepository.findByCompanyIdAndRoleNameContaining(
                CompanyScope.currentCompanyId(), keyword).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Add parent role (role inheritance)
     *
     * @param childRoleId Child role ID (inheritor)
     * @param parentRoleId Parent role ID (inherited from)
     * @throws IllegalArgumentException if roles not found or circular inheritance detected
     */
    @Transactional
    public void addRoleInheritance(Long childRoleId, Long parentRoleId) {
        Long companyId = CompanyScope.currentCompanyId();
        log.info("Adding role inheritance: child={}, parent={}", childRoleId, parentRoleId);

        // Validate roles exist
        SysRole childRole = roleRepository.findByCompanyIdAndId(companyId, childRoleId)
                .orElseThrow(() -> new IllegalArgumentException("Child role not found: " + childRoleId));
        SysRole parentRole = roleRepository.findByCompanyIdAndId(companyId, parentRoleId)
                .orElseThrow(() -> new IllegalArgumentException("Parent role not found: " + parentRoleId));

        if (!childRole.isPrivilegedRole() && parentRole.isPrivilegedRole()) {
            throw new IllegalArgumentException(
                    "Non-privileged roles cannot inherit a privileged role: " + parentRole.getRoleCode());
        }

        // Check if already exists
        if (roleInheritRepository.existsByCompanyIdAndChildRoleIdAndParentRoleId(
                companyId, childRoleId, parentRoleId)) {
            log.warn("Role inheritance already exists: child={}, parent={}", childRoleId, parentRoleId);
            return;
        }

        if (wouldCreateCircularInheritance(childRoleId, parentRoleId)) {
            throw new IllegalArgumentException(String.format(
                "Circular inheritance detected: childRole=%s, parentRole=%s",
                childRole.getRoleCode(), parentRole.getRoleCode()
            ));
        }

        // Create inheritance relationship
        SysRoleInherit inherit = SysRoleInherit.builder()
                .companyId(companyId)
                .childRoleId(childRoleId)
                .parentRoleId(parentRoleId)
                .build();

        roleInheritRepository.save(inherit);

        // Invalidate cache
        cacheService.onRoleInheritanceChanged(childRoleId, parentRoleId);
        securityVersionService.bumpForRoleAndDescendants(childRoleId);

        log.info("Role inheritance added successfully");
    }

    /**
     * Remove parent role (role inheritance)
     *
     * @param childRoleId Child role ID
     * @param parentRoleId Parent role ID
     */
    @Transactional
    public void removeRoleInheritance(Long childRoleId, Long parentRoleId) {
        log.info("Removing role inheritance: child={}, parent={}", childRoleId, parentRoleId);

        roleInheritRepository.deleteByCompanyIdAndChildRoleIdAndParentRoleId(
            CompanyScope.currentCompanyId(), childRoleId, parentRoleId);

        // Invalidate cache
        cacheService.onRoleInheritanceChanged(childRoleId, parentRoleId);
        securityVersionService.bumpForRoleAndDescendants(childRoleId);

        log.info("Role inheritance removed successfully");
    }

    /**
     * Get parent role IDs for a role
     *
     * @param roleId Role ID
     * @return Set of parent role IDs
     */
    public Set<Long> getParentRoleIds(Long roleId) {
        return roleInheritRepository.findParentRoleIdsByCompanyIdAndChildRoleId(
            CompanyScope.currentCompanyId(), roleId);
    }

    // ========== Helper Methods ==========

    private boolean wouldCreateCircularInheritance(Long childRoleId, Long parentRoleId) {
        if (childRoleId.equals(parentRoleId)) {
            return true;
        }

        Set<Long> visited = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(parentRoleId);

        while (!stack.isEmpty()) {
            Long current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            if (childRoleId.equals(current)) {
                return true;
            }

            Set<Long> parents = roleInheritRepository.findParentRoleIdsByCompanyIdAndChildRoleId(
                CompanyScope.currentCompanyId(), current);
            if (parents != null && !parents.isEmpty()) {
                parents.forEach(stack::push);
            }
        }

        return false;
    }

    /**
     * Convert SysRole entity to RoleDTO
     *
     * @param role SysRole entity
     * @return RoleDTO
     */
    private RoleDTO convertToDTO(SysRole role) {
        // Get parent role IDs
        Set<Long> parentRoleIds = roleInheritRepository
            .findParentRoleIdsByCompanyIdAndChildRoleId(role.getCompanyId(), role.getId());

        // Get permission IDs
        Set<Long> permissionIds = rolePermissionRepository
            .findPermissionIdsByCompanyIdAndRoleId(role.getCompanyId(), role.getId());

        return RoleDTO.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .roleType(role.getRoleType())
                .systemCategory(role.getSystemCategory())
                .importAllowed(role.canImportPermissions())
                .approvalTemplateCode(role.getApprovalTemplateCode())
                .reviewStatus(role.getReviewStatus())
                .reviewSubmittedBy(role.getReviewSubmittedBy())
                .reviewSubmittedByUsername(role.getReviewSubmittedByUsername())
                .reviewSubmittedAt(role.getReviewSubmittedAt())
                .reviewedBy(role.getReviewedBy())
                .reviewedByUsername(role.getReviewedByUsername())
                .reviewedAt(role.getReviewedAt())
                .reviewComment(role.getReviewComment())
                .status(role.getStatus())
                .sortOrder(role.getSortOrder())
                .parentRoleIds(parentRoleIds)
                .permissionIds(permissionIds)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}

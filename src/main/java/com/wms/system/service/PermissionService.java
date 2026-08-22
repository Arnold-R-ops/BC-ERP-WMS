package com.wms.system.service;

import com.wms.system.dto.MenuTreeDTO;
import com.wms.system.dto.PermissionDTO;
import com.wms.system.entity.SysPermission;
import com.wms.system.repository.SysPermissionRepository;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Permission Management Service
 *
 * Provides CRUD operations for system permissions.
 * Builds permission tree structures for frontend rendering.
 *
 * Key Features:
 * - Create, update, delete permissions
 * - Query permissions by various criteria
 * - Build menu tree for frontend
 * - Support for hierarchical permission structure
 * - Cache invalidation after permission changes
 *
 * Permission Types:
 * - MENU: Frontend menu items
 * - BUTTON: Frontend button controls
 * - API: Backend endpoint access
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final SysPermissionRepository permissionRepository;
    private final PermissionCacheService cacheService;
    private final SecurityVersionService securityVersionService;

    /**
     * Create a new permission
     *
     * @param permissionDTO Permission data
     * @return Created permission DTO
     * @throws IllegalArgumentException if permission code already exists
     */
    @Transactional
    public PermissionDTO createPermission(PermissionDTO permissionDTO) {
        Long companyId = CompanyScope.currentCompanyId();
        log.info("Creating new permission: {}", permissionDTO.getPermissionCode());

        // Validate permission code uniqueness
        if (permissionRepository.existsByCompanyIdAndPermissionCode(
                companyId, permissionDTO.getPermissionCode())) {
            throw new IllegalArgumentException("Permission code already exists: " + permissionDTO.getPermissionCode());
        }

        boolean reservedPermission = SysPermission.isReservedPermissionCode(permissionDTO.getPermissionCode());
        String riskLevel = resolveRiskLevel(permissionDTO.getRiskLevel(), SysPermission.RISK_LEVEL_NORMAL);
        boolean customAssignable = permissionDTO.getCustomAssignable() == null
                || Boolean.TRUE.equals(permissionDTO.getCustomAssignable());
        if (reservedPermission || SysPermission.RISK_LEVEL_CRITICAL.equals(riskLevel)) {
            riskLevel = SysPermission.RISK_LEVEL_CRITICAL;
            customAssignable = false;
        }

        // Create permission entity
        SysPermission permission = SysPermission.builder()
                .permissionCode(permissionDTO.getPermissionCode())
                .permissionName(permissionDTO.getPermissionName())
                .permissionType(permissionDTO.getPermissionType())
                .parentId(permissionDTO.getParentId())
                .resourcePath(permissionDTO.getResourcePath())
                .httpMethod(permissionDTO.getHttpMethod())
                .menuUrl(permissionDTO.getMenuUrl())
                .menuIcon(permissionDTO.getMenuIcon())
                .dataScope(permissionDTO.getDataScope() != null ? permissionDTO.getDataScope() : "ALL")
                .riskLevel(riskLevel)
                .customAssignable(customAssignable)
                .description(permissionDTO.getDescription())
                .status(permissionDTO.getStatus() != null ? permissionDTO.getStatus() : "ACTIVE")
                .sortOrder(permissionDTO.getSortOrder() != null ? permissionDTO.getSortOrder() : 0)
                .build();

        // Save permission
        permission.setCompanyId(companyId);
        permission = permissionRepository.save(permission);

        // Invalidate cache
        cacheService.onPermissionChanged(permission.getId());
        securityVersionService.bumpAllUsers();

        log.info("Permission created successfully: {} (ID: {})", permission.getPermissionCode(), permission.getId());

        return convertToDTO(permission);
    }

    /**
     * Update an existing permission
     *
     * @param permissionId Permission ID
     * @param permissionDTO Updated permission data
     * @return Updated permission DTO
     * @throws IllegalArgumentException if permission not found
     */
    @Transactional
    public PermissionDTO updatePermission(Long permissionId, PermissionDTO permissionDTO) {
        log.info("Updating permission ID: {}", permissionId);

        // Find existing permission
        SysPermission permission = permissionRepository.findByCompanyIdAndId(
                CompanyScope.currentCompanyId(), permissionId)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permissionId));

        // Update fields
        permission.setPermissionName(permissionDTO.getPermissionName());
        permission.setResourcePath(permissionDTO.getResourcePath());
        permission.setHttpMethod(permissionDTO.getHttpMethod());
        permission.setMenuUrl(permissionDTO.getMenuUrl());
        permission.setMenuIcon(permissionDTO.getMenuIcon());
        permission.setDataScope(permissionDTO.getDataScope());
        String riskLevel = resolveRiskLevel(permissionDTO.getRiskLevel(), permission.getRiskLevel());
        boolean customAssignable = permissionDTO.getCustomAssignable() != null
                ? Boolean.TRUE.equals(permissionDTO.getCustomAssignable())
                : permission.isCustomAssignable();
        if (SysPermission.isReservedPermissionCode(permission.getPermissionCode())
                || SysPermission.RISK_LEVEL_CRITICAL.equals(riskLevel)) {
            riskLevel = SysPermission.RISK_LEVEL_CRITICAL;
            customAssignable = false;
        }
        permission.setRiskLevel(riskLevel);
        permission.setCustomAssignable(customAssignable);
        permission.setDescription(permissionDTO.getDescription());
        permission.setStatus(permissionDTO.getStatus());
        permission.setSortOrder(permissionDTO.getSortOrder());

        // Save changes
        permission = permissionRepository.save(permission);

        // Invalidate cache
        cacheService.onPermissionChanged(permissionId);
        securityVersionService.bumpAllUsers();

        log.info("Permission updated successfully: {} (ID: {})", permission.getPermissionCode(), permission.getId());

        return convertToDTO(permission);
    }

    /**
     * Delete a permission
     *
     * @param permissionId Permission ID
     * @throws IllegalArgumentException if permission not found or has children
     */
    @Transactional
    public void deletePermission(Long permissionId) {
        Long companyId = CompanyScope.currentCompanyId();
        log.info("Deleting permission ID: {}", permissionId);

        // Find permission
        SysPermission permission = permissionRepository.findByCompanyIdAndId(companyId, permissionId)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permissionId));

        // Check if has child permissions
        List<SysPermission> children = permissionRepository
            .findByCompanyIdAndParentId(companyId, permissionId);
        if (!children.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Cannot delete permission with %d child permissions", children.size()));
        }

        // Delete permission (cascade will delete associations)
        permissionRepository.delete(permission);

        // Invalidate cache
        cacheService.onPermissionChanged(permissionId);
        securityVersionService.bumpAllUsers();

        log.info("Permission deleted successfully: {} (ID: {})", permission.getPermissionCode(), permission.getId());
    }

    /**
     * Get permission by ID
     *
     * @param permissionId Permission ID
     * @return Permission DTO
     */
    public PermissionDTO getPermissionById(Long permissionId) {
        SysPermission permission = permissionRepository.findByCompanyIdAndId(
                CompanyScope.currentCompanyId(), permissionId)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permissionId));

        return convertToDTO(permission);
    }

    /**
     * Get permission by permission code
     *
     * @param permissionCode Permission code
     * @return Permission DTO
     */
    public PermissionDTO getPermissionByCode(String permissionCode) {
        SysPermission permission = permissionRepository.findByCompanyIdAndPermissionCode(
                CompanyScope.currentCompanyId(), permissionCode)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permissionCode));

        return convertToDTO(permission);
    }

    /**
     * Get all permissions
     *
     * @return List of permission DTOs
     */
    public List<PermissionDTO> getAllPermissions() {
        return permissionRepository.findByCompanyIdOrderBySortOrderAsc(
                CompanyScope.currentCompanyId()).stream()
                .map(this::convertToDTO)
                .sorted(Comparator.comparing(PermissionDTO::getSortOrder))
                .collect(Collectors.toList());
    }

    /**
     * Get all active permissions
     *
     * @return List of active permission DTOs
     */
    public List<PermissionDTO> getAllActivePermissions() {
        return permissionRepository.findByCompanyIdAndStatus(
                CompanyScope.currentCompanyId(), "ACTIVE").stream()
                .map(this::convertToDTO)
                .sorted(Comparator.comparing(PermissionDTO::getSortOrder))
                .collect(Collectors.toList());
    }

    /**
     * Get permissions by type
     *
     * @param permissionType Permission type (MENU, BUTTON, API)
     * @return List of permission DTOs
     */
    public List<PermissionDTO> getPermissionsByType(String permissionType) {
        return permissionRepository.findByCompanyIdAndPermissionType(
                CompanyScope.currentCompanyId(), permissionType).stream()
                .map(this::convertToDTO)
                .sorted(Comparator.comparing(PermissionDTO::getSortOrder))
                .collect(Collectors.toList());
    }

    /**
     * Get child permissions
     *
     * @param parentId Parent permission ID
     * @return List of child permission DTOs
     */
    public List<PermissionDTO> getChildPermissions(Long parentId) {
        return permissionRepository.findByCompanyIdAndParentId(
                CompanyScope.currentCompanyId(), parentId).stream()
                .map(this::convertToDTO)
                .sorted(Comparator.comparing(PermissionDTO::getSortOrder))
                .collect(Collectors.toList());
    }

    /**
     * Search permissions by name
     *
     * @param keyword Search keyword
     * @return List of matching permission DTOs
     */
    public List<PermissionDTO> searchPermissions(String keyword) {
        return permissionRepository.findByCompanyIdAndPermissionNameContaining(
                CompanyScope.currentCompanyId(), keyword).stream()
                .map(this::convertToDTO)
                .sorted(Comparator.comparing(PermissionDTO::getSortOrder))
                .collect(Collectors.toList());
    }

    /**
     * Build menu tree for frontend rendering
     *
     * Returns hierarchical structure of menu permissions.
     * Only includes MENU type permissions with ACTIVE status.
     *
     * @return List of root menu tree nodes
     */
    public List<MenuTreeDTO> buildMenuTree() {
        log.debug("Building menu tree");

        // Get all active menu permissions
        List<SysPermission> allMenus = permissionRepository
            .findByCompanyIdAndPermissionTypeAndStatus(
                CompanyScope.currentCompanyId(), "MENU", "ACTIVE");

        // Build map for quick lookup
        Map<Long, MenuTreeDTO> menuMap = new HashMap<>();
        List<MenuTreeDTO> roots = new ArrayList<>();

        // Convert to DTOs
        for (SysPermission menu : allMenus) {
            MenuTreeDTO menuDTO = MenuTreeDTO.builder()
                    .id(menu.getId())
                    .permissionCode(menu.getPermissionCode())
                    .menuName(menu.getPermissionName())
                    .menuUrl(menu.getMenuUrl())
                    .menuIcon(menu.getMenuIcon())
                    .parentId(menu.getParentId())
                    .sortOrder(menu.getSortOrder())
                    .children(new ArrayList<>())
                    .build();

            menuMap.put(menu.getId(), menuDTO);

            // Root node (no parent)
            if (menu.getParentId() == null) {
                roots.add(menuDTO);
            }
        }

        // Build tree structure
        for (SysPermission menu : allMenus) {
            if (menu.getParentId() != null) {
                MenuTreeDTO parent = menuMap.get(menu.getParentId());
                MenuTreeDTO child = menuMap.get(menu.getId());
                if (parent != null && child != null) {
                    parent.addChild(child);
                }
            }
        }

        // Sort roots by sortOrder
        roots.sort(Comparator.comparing(MenuTreeDTO::getSortOrder));

        // Sort children recursively
        roots.forEach(this::sortMenuChildren);

        log.debug("Menu tree built successfully: {} root nodes", roots.size());

        return roots;
    }

    /**
     * Sort menu children recursively
     *
     * @param menu Menu node
     */
    private void sortMenuChildren(MenuTreeDTO menu) {
        if (menu.getChildren() != null && !menu.getChildren().isEmpty()) {
            menu.getChildren().sort(Comparator.comparing(MenuTreeDTO::getSortOrder));
            menu.getChildren().forEach(this::sortMenuChildren);
        }
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

    private String resolveRiskLevel(String requestedRiskLevel, String fallbackRiskLevel) {
        String riskLevel = requestedRiskLevel != null ? requestedRiskLevel : fallbackRiskLevel;
        if (riskLevel == null) {
            return SysPermission.RISK_LEVEL_NORMAL;
        }
        if (!Set.of(
                SysPermission.RISK_LEVEL_NORMAL,
                SysPermission.RISK_LEVEL_HIGH,
                SysPermission.RISK_LEVEL_CRITICAL
        ).contains(riskLevel)) {
            throw new IllegalArgumentException("Invalid permission risk level: " + riskLevel);
        }
        return riskLevel;
    }
}

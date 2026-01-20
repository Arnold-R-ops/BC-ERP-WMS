package com.wms.system.service;

import com.wms.system.dto.MenuTreeDTO;
import com.wms.system.dto.PermissionDTO;
import com.wms.system.entity.SysPermission;
import com.wms.system.repository.SysPermissionRepository;
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

    /**
     * Create a new permission
     *
     * @param permissionDTO Permission data
     * @return Created permission DTO
     * @throws IllegalArgumentException if permission code already exists
     */
    @Transactional
    public PermissionDTO createPermission(PermissionDTO permissionDTO) {
        log.info("Creating new permission: {}", permissionDTO.getPermissionCode());

        // Validate permission code uniqueness
        if (permissionRepository.existsByPermissionCode(permissionDTO.getPermissionCode())) {
            throw new IllegalArgumentException("Permission code already exists: " + permissionDTO.getPermissionCode());
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
                .description(permissionDTO.getDescription())
                .status(permissionDTO.getStatus() != null ? permissionDTO.getStatus() : "ACTIVE")
                .sortOrder(permissionDTO.getSortOrder() != null ? permissionDTO.getSortOrder() : 0)
                .build();

        // Save permission
        permission = permissionRepository.save(permission);

        // Invalidate cache
        cacheService.onPermissionChanged(permission.getId());

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
        SysPermission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permissionId));

        // Update fields
        permission.setPermissionName(permissionDTO.getPermissionName());
        permission.setResourcePath(permissionDTO.getResourcePath());
        permission.setHttpMethod(permissionDTO.getHttpMethod());
        permission.setMenuUrl(permissionDTO.getMenuUrl());
        permission.setMenuIcon(permissionDTO.getMenuIcon());
        permission.setDataScope(permissionDTO.getDataScope());
        permission.setDescription(permissionDTO.getDescription());
        permission.setStatus(permissionDTO.getStatus());
        permission.setSortOrder(permissionDTO.getSortOrder());

        // Save changes
        permission = permissionRepository.save(permission);

        // Invalidate cache
        cacheService.onPermissionChanged(permissionId);

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
        log.info("Deleting permission ID: {}", permissionId);

        // Find permission
        SysPermission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permissionId));

        // Check if has child permissions
        List<SysPermission> children = permissionRepository.findByParentId(permissionId);
        if (!children.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Cannot delete permission with %d child permissions", children.size()));
        }

        // Delete permission (cascade will delete associations)
        permissionRepository.delete(permission);

        // Invalidate cache
        cacheService.onPermissionChanged(permissionId);

        log.info("Permission deleted successfully: {} (ID: {})", permission.getPermissionCode(), permission.getId());
    }

    /**
     * Get permission by ID
     *
     * @param permissionId Permission ID
     * @return Permission DTO
     */
    public PermissionDTO getPermissionById(Long permissionId) {
        SysPermission permission = permissionRepository.findById(permissionId)
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
        SysPermission permission = permissionRepository.findByPermissionCode(permissionCode)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permissionCode));

        return convertToDTO(permission);
    }

    /**
     * Get all permissions
     *
     * @return List of permission DTOs
     */
    public List<PermissionDTO> getAllPermissions() {
        return permissionRepository.findAll().stream()
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
        return permissionRepository.findAllActive().stream()
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
        return permissionRepository.findByPermissionType(permissionType).stream()
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
        return permissionRepository.findByParentId(parentId).stream()
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
        return permissionRepository.findByPermissionNameContaining(keyword).stream()
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
        List<SysPermission> allMenus = permissionRepository.findByPermissionTypeAndStatus("MENU", "ACTIVE");

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
                .description(permission.getDescription())
                .status(permission.getStatus())
                .sortOrder(permission.getSortOrder())
                .build();
    }
}

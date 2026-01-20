package com.wms.system.repository;

import com.wms.system.entity.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * System Permission Repository
 *
 * Data access interface for sys_permission table.
 * Manages menu, button, and API permissions.
 *
 * Permission Types:
 * - MENU: Frontend menu permissions
 * - BUTTON: Frontend button permissions
 * - API: Backend API endpoint permissions
 *
 * Tree Structure:
 * - Root permissions: parent_id IS NULL
 * - Child permissions: parent_id points to parent
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Repository
public interface SysPermissionRepository extends JpaRepository<SysPermission, Long> {

    /**
     * Find permission by permission code
     *
     * @param permissionCode Permission code (e.g., inventory:view)
     * @return Optional<SysPermission>
     */
    Optional<SysPermission> findByPermissionCode(String permissionCode);

    /**
     * Check if permission code exists
     *
     * @param permissionCode Permission code
     * @return true if exists
     */
    boolean existsByPermissionCode(String permissionCode);

    /**
     * Find permissions by permission type
     *
     * @param permissionType Permission type (MENU, BUTTON, API)
     * @return List of permissions
     */
    List<SysPermission> findByPermissionType(String permissionType);

    /**
     * Find permissions by parent ID (for tree structure)
     *
     * @param parentId Parent permission ID
     * @return List of child permissions
     */
    List<SysPermission> findByParentId(Long parentId);

    /**
     * Find root permissions (parent_id IS NULL)
     *
     * @return List of root permissions
     */
    @Query("SELECT p FROM SysPermission p WHERE p.parentId IS NULL ORDER BY p.sortOrder ASC")
    List<SysPermission> findRootPermissions();

    /**
     * Find permissions by status
     *
     * @param status Permission status (ACTIVE, DISABLED)
     * @return List of permissions
     */
    List<SysPermission> findByStatus(String status);

    /**
     * Find permissions by type and status
     *
     * @param permissionType Permission type
     * @param status Permission status
     * @return List of permissions
     */
    List<SysPermission> findByPermissionTypeAndStatus(String permissionType, String status);

    /**
     * Find all active permissions
     *
     * @return List of active permissions
     */
    default List<SysPermission> findAllActive() {
        return findByStatus("ACTIVE");
    }

    /**
     * Find all menu permissions
     *
     * @return List of menu permissions
     */
    default List<SysPermission> findAllMenus() {
        return findByPermissionType("MENU");
    }

    /**
     * Find all API permissions
     *
     * @return List of API permissions
     */
    default List<SysPermission> findAllApis() {
        return findByPermissionType("API");
    }

    /**
     * Find permissions by ID set (for batch queries)
     *
     * @param permissionIds Set of permission IDs
     * @return List of permissions
     */
    List<SysPermission> findByIdIn(Set<Long> permissionIds);

    /**
     * Find permissions ordered by sort order
     *
     * @return List of permissions ordered by sort_order ASC
     */
    List<SysPermission> findAllByOrderBySortOrderAsc();

    /**
     * Search permissions by name (fuzzy match)
     *
     * @param keyword Search keyword
     * @return List of matching permissions
     */
    List<SysPermission> findByPermissionNameContaining(String keyword);

    /**
     * Find API permissions by resource path (for authorization)
     *
     * @param resourcePath Resource path pattern
     * @return List of matching API permissions
     */
    List<SysPermission> findByResourcePath(String resourcePath);

    /**
     * Find API permissions by HTTP method
     *
     * @param httpMethod HTTP method (GET, POST, PUT, DELETE, *)
     * @return List of matching API permissions
     */
    List<SysPermission> findByHttpMethod(String httpMethod);

    /**
     * Find permission tree structure
     * Returns all permissions with their hierarchy relationship
     *
     * @return List of all permissions (use in service layer to build tree)
     */
    @Query("SELECT p FROM SysPermission p WHERE p.status = 'ACTIVE' ORDER BY p.parentId ASC, p.sortOrder ASC")
    List<SysPermission> findAllActiveForTree();
}

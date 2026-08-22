package com.wms.system.repository;

import com.wms.system.entity.SysRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * Role-Permission Association Repository
 *
 * Data access interface for sys_role_permission table.
 * Manages many-to-many relationship between roles and permissions.
 *
 * Common Operations:
 * - Assign permission to role
 * - Remove permission from role
 * - Query role's permissions
 * - Query permission's roles
 *
 * Critical for Dynamic Authorization:
 * - Used to load user permissions dynamically
 * - Supports role inheritance (query parent roles' permissions)
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Repository
public interface SysRolePermissionRepository extends JpaRepository<SysRolePermission, Long> {

    /**
     * Find role-permission associations by role ID
     *
     * @param roleId Role ID
     * @return List of role-permission associations
     */
    List<SysRolePermission> findByRoleId(Long roleId);

    List<SysRolePermission> findByCompanyIdAndRoleId(Long companyId, Long roleId);

    /**
     * Find role-permission associations by permission ID
     *
     * @param permissionId Permission ID
     * @return List of role-permission associations
     */
    List<SysRolePermission> findByPermissionId(Long permissionId);

    List<SysRolePermission> findByCompanyIdAndPermissionId(
        Long companyId, Long permissionId);

    /**
     * Check if role has specific permission
     *
     * @param roleId Role ID
     * @param permissionId Permission ID
     * @return true if association exists
     */
    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);

    boolean existsByCompanyIdAndRoleIdAndPermissionId(
        Long companyId, Long roleId, Long permissionId);

    /**
     * Delete role-permission association
     *
     * @param roleId Role ID
     * @param permissionId Permission ID
     */
    @Modifying
    @Query("DELETE FROM SysRolePermission srp WHERE srp.roleId = :roleId AND srp.permissionId = :permissionId")
    void deleteByRoleIdAndPermissionId(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Modifying
    @Query("DELETE FROM SysRolePermission srp WHERE srp.companyId = :companyId AND srp.roleId = :roleId AND srp.permissionId = :permissionId")
    void deleteByCompanyIdAndRoleIdAndPermissionId(
        @Param("companyId") Long companyId,
        @Param("roleId") Long roleId,
        @Param("permissionId") Long permissionId
    );

    /**
     * Delete all permissions for a role
     *
     * @param roleId Role ID
     */
    @Modifying
    void deleteByRoleId(Long roleId);

    @Modifying
    void deleteByCompanyIdAndRoleId(Long companyId, Long roleId);

    /**
     * Delete all roles for a permission
     *
     * @param permissionId Permission ID
     */
    @Modifying
    void deleteByPermissionId(Long permissionId);

    /**
     * Get all permission IDs for a role
     *
     * @param roleId Role ID
     * @return Set of permission IDs
     */
    @Query("SELECT srp.permissionId FROM SysRolePermission srp WHERE srp.roleId = :roleId")
    Set<Long> findPermissionIdsByRoleId(@Param("roleId") Long roleId);

    @Query("SELECT srp.permissionId FROM SysRolePermission srp WHERE srp.companyId = :companyId AND srp.roleId = :roleId")
    Set<Long> findPermissionIdsByCompanyIdAndRoleId(
        @Param("companyId") Long companyId,
        @Param("roleId") Long roleId
    );

    /**
     * Get all role IDs for a permission
     *
     * @param permissionId Permission ID
     * @return Set of role IDs
     */
    @Query("SELECT srp.roleId FROM SysRolePermission srp WHERE srp.permissionId = :permissionId")
    Set<Long> findRoleIdsByPermissionId(@Param("permissionId") Long permissionId);

    @Query("SELECT srp.roleId FROM SysRolePermission srp WHERE srp.companyId = :companyId AND srp.permissionId = :permissionId")
    Set<Long> findRoleIdsByCompanyIdAndPermissionId(
        @Param("companyId") Long companyId,
        @Param("permissionId") Long permissionId
    );

    /**
     * Find role-permission associations by role ID set (for batch queries)
     * CRITICAL: Used for loading user permissions with role inheritance
     *
     * @param roleIds Set of role IDs (including inherited role IDs)
     * @return List of role-permission associations
     */
    @Query("SELECT srp FROM SysRolePermission srp WHERE srp.roleId IN :roleIds")
    List<SysRolePermission> findByRoleIdIn(@Param("roleIds") Set<Long> roleIds);

    @Query("SELECT srp FROM SysRolePermission srp WHERE srp.companyId = :companyId AND srp.roleId IN :roleIds")
    List<SysRolePermission> findByCompanyIdAndRoleIdIn(
        @Param("companyId") Long companyId,
        @Param("roleIds") Set<Long> roleIds
    );

    /**
     * Find role-permission associations with permission details (eager loading)
     * CRITICAL: Used for dynamic authorization
     *
     * @param roleIds Set of role IDs
     * @return List of role-permission associations with permission entity
     */
    @Query("SELECT srp FROM SysRolePermission srp " +
           "JOIN FETCH srp.permission p " +
           "JOIN FETCH srp.role r " +
           "WHERE srp.roleId IN :roleIds " +
           "AND p.status = 'ACTIVE' " +
           "AND (r.roleType = 'SYSTEM' OR p.customAssignable = true)")
    List<SysRolePermission> findByRoleIdInWithPermission(@Param("roleIds") Set<Long> roleIds);

    @Query("SELECT srp FROM SysRolePermission srp " +
           "JOIN FETCH srp.permission p " +
           "JOIN FETCH srp.role r " +
           "WHERE srp.companyId = :companyId " +
           "AND p.companyId = :companyId " +
           "AND r.companyId = :companyId " +
           "AND srp.roleId IN :roleIds " +
           "AND p.status = 'ACTIVE' " +
           "AND (r.roleType = 'SYSTEM' OR p.customAssignable = true)")
    List<SysRolePermission> findByCompanyIdAndRoleIdInWithPermission(
        @Param("companyId") Long companyId,
        @Param("roleIds") Set<Long> roleIds
    );

    /**
     * Count permissions for specific role
     *
     * @param roleId Role ID
     * @return Number of permissions
     */
    long countByRoleId(Long roleId);

    long countByCompanyIdAndRoleId(Long companyId, Long roleId);

    /**
     * Count roles with specific permission
     *
     * @param permissionId Permission ID
     * @return Number of roles
     */
    long countByPermissionId(Long permissionId);

    long countByCompanyIdAndPermissionId(Long companyId, Long permissionId);

    /**
     * Find all API permissions for a role (for authorization)
     *
     * @param roleId Role ID
     * @return List of role-permission associations (API type only)
     */
    @Query("SELECT srp FROM SysRolePermission srp JOIN FETCH srp.permission p " +
           "WHERE srp.roleId = :roleId AND p.permissionType = 'API' AND p.status = 'ACTIVE'")
    List<SysRolePermission> findApiPermissionsByRoleId(@Param("roleId") Long roleId);

    /**
     * Find all menu permissions for a role (for frontend rendering)
     *
     * @param roleId Role ID
     * @return List of role-permission associations (MENU type only)
     */
    @Query("SELECT srp FROM SysRolePermission srp JOIN FETCH srp.permission p " +
           "WHERE srp.roleId = :roleId AND p.permissionType = 'MENU' AND p.status = 'ACTIVE'")
    List<SysRolePermission> findMenuPermissionsByRoleId(@Param("roleId") Long roleId);
}

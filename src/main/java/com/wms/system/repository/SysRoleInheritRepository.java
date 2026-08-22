package com.wms.system.repository;

import com.wms.system.entity.SysRoleInherit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * Role Inheritance Association Repository
 *
 * Data access interface for sys_role_inherit table.
 * Manages role inheritance relationships for complex permission scenarios.
 *
 * Inheritance Model:
 * - child_role_id: The role that inherits (e.g., CHAIRMAN)
 * - parent_role_id: The role being inherited from (e.g., WAREHOUSE_ADMIN)
 *
 * Key Use Cases:
 * - CHAIRMAN inherits from [WAREHOUSE_ADMIN, BUYER, SELLER]
 * - Recursive query to get all inherited role IDs
 * - Support for multi-level inheritance
 *
 * Performance Note:
 * - Results should be cached to avoid repeated recursive queries
 * - Use with DynamicPermissionService for efficient permission loading
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Repository
public interface SysRoleInheritRepository extends JpaRepository<SysRoleInherit, Long> {

    /**
     * Find all parent roles for a child role
     *
     * @param childRoleId Child role ID (inheritor)
     * @return List of inheritance relationships
     */
    List<SysRoleInherit> findByChildRoleId(Long childRoleId);

    List<SysRoleInherit> findByCompanyIdAndChildRoleId(Long companyId, Long childRoleId);

    /**
     * Find all child roles for a parent role
     *
     * @param parentRoleId Parent role ID (inherited from)
     * @return List of inheritance relationships
     */
    List<SysRoleInherit> findByParentRoleId(Long parentRoleId);

    List<SysRoleInherit> findByCompanyIdAndParentRoleId(Long companyId, Long parentRoleId);

    /**
     * Check if inheritance relationship exists
     *
     * @param childRoleId Child role ID
     * @param parentRoleId Parent role ID
     * @return true if inheritance exists
     */
    boolean existsByChildRoleIdAndParentRoleId(Long childRoleId, Long parentRoleId);

    boolean existsByCompanyIdAndChildRoleIdAndParentRoleId(
        Long companyId, Long childRoleId, Long parentRoleId);

    /**
     * Delete inheritance relationship
     *
     * @param childRoleId Child role ID
     * @param parentRoleId Parent role ID
     */
    @Modifying
    @Query("DELETE FROM SysRoleInherit sri WHERE sri.childRoleId = :childRoleId AND sri.parentRoleId = :parentRoleId")
    void deleteByChildRoleIdAndParentRoleId(@Param("childRoleId") Long childRoleId, @Param("parentRoleId") Long parentRoleId);

    @Modifying
    @Query("DELETE FROM SysRoleInherit sri WHERE sri.companyId = :companyId AND sri.childRoleId = :childRoleId AND sri.parentRoleId = :parentRoleId")
    void deleteByCompanyIdAndChildRoleIdAndParentRoleId(
        @Param("companyId") Long companyId,
        @Param("childRoleId") Long childRoleId,
        @Param("parentRoleId") Long parentRoleId
    );

    /**
     * Delete all parent roles for a child role
     *
     * @param childRoleId Child role ID
     */
    @Modifying
    void deleteByChildRoleId(Long childRoleId);

    /**
     * Delete all child roles for a parent role
     *
     * @param parentRoleId Parent role ID
     */
    @Modifying
    void deleteByParentRoleId(Long parentRoleId);

    /**
     * Get all parent role IDs for a child role
     * CRITICAL: Used for role inheritance resolution
     *
     * @param childRoleId Child role ID
     * @return Set of parent role IDs
     */
    @Query("SELECT sri.parentRoleId FROM SysRoleInherit sri WHERE sri.childRoleId = :childRoleId")
    Set<Long> findParentRoleIdsByChildRoleId(@Param("childRoleId") Long childRoleId);

    @Query("SELECT sri.parentRoleId FROM SysRoleInherit sri WHERE sri.companyId = :companyId AND sri.childRoleId = :childRoleId")
    Set<Long> findParentRoleIdsByCompanyIdAndChildRoleId(
        @Param("companyId") Long companyId,
        @Param("childRoleId") Long childRoleId
    );

    /**
     * Get all child role IDs for a parent role
     *
     * @param parentRoleId Parent role ID
     * @return Set of child role IDs
     */
    @Query("SELECT sri.childRoleId FROM SysRoleInherit sri WHERE sri.parentRoleId = :parentRoleId")
    Set<Long> findChildRoleIdsByParentRoleId(@Param("parentRoleId") Long parentRoleId);

    @Query("SELECT sri.childRoleId FROM SysRoleInherit sri WHERE sri.companyId = :companyId AND sri.parentRoleId = :parentRoleId")
    Set<Long> findChildRoleIdsByCompanyIdAndParentRoleId(
        @Param("companyId") Long companyId,
        @Param("parentRoleId") Long parentRoleId
    );

    /**
     * Get all parent role IDs for multiple child roles (batch query)
     *
     * @param childRoleIds Set of child role IDs
     * @return Set of parent role IDs
     */
    @Query("SELECT sri.parentRoleId FROM SysRoleInherit sri WHERE sri.childRoleId IN :childRoleIds")
    Set<Long> findParentRoleIdsByChildRoleIdIn(@Param("childRoleIds") Set<Long> childRoleIds);

    /**
     * Count parent roles for a child role
     *
     * @param childRoleId Child role ID
     * @return Number of parent roles
     */
    long countByChildRoleId(Long childRoleId);

    /**
     * Count child roles for a parent role
     *
     * @param parentRoleId Parent role ID
     * @return Number of child roles
     */
    long countByParentRoleId(Long parentRoleId);

    /**
     * Find inheritance relationships with role details (eager loading)
     *
     * @param childRoleId Child role ID
     * @return List of inheritance relationships with parent role entity
     */
    @Query("SELECT sri FROM SysRoleInherit sri JOIN FETCH sri.parentRole WHERE sri.childRoleId = :childRoleId")
    List<SysRoleInherit> findByChildRoleIdWithParentRole(@Param("childRoleId") Long childRoleId);

    /**
     * Recursive query to get all inherited role IDs (including multi-level inheritance)
     * Uses PostgreSQL recursive CTE for efficient querying
     *
     * Example: If A inherits B, and B inherits C, this returns [B, C] for input A
     *
     * @param childRoleId Starting child role ID
     * @return Set of all inherited role IDs (direct and indirect)
     */
    @Query(value = "WITH RECURSIVE role_tree AS (" +
            "SELECT parent_role_id AS role_id FROM sys_role_inherit " +
            "WHERE company_id = :companyId AND child_role_id = :childRoleId " +
            "UNION " +
            "SELECT sri.parent_role_id FROM sys_role_inherit sri " +
            "INNER JOIN role_tree rt ON sri.child_role_id = rt.role_id " +
            "WHERE sri.company_id = :companyId" +
            ") SELECT DISTINCT role_id FROM role_tree",
            nativeQuery = true)
    Set<Long> findAllInheritedRoleIds(
            @Param("companyId") Long companyId,
            @Param("childRoleId") Long childRoleId);
}

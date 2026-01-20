package com.wms.system.repository;

import com.wms.system.entity.SysUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * User-Role Association Repository
 *
 * Data access interface for sys_user_role table.
 * Manages many-to-many relationship between users and roles.
 *
 * Common Operations:
 * - Assign role to user
 * - Remove role from user
 * - Query user's roles
 * - Query role's users
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Repository
public interface SysUserRoleRepository extends JpaRepository<SysUserRole, Long> {

    /**
     * Find user-role associations by user ID
     *
     * @param userId User ID
     * @return List of user-role associations
     */
    List<SysUserRole> findByUserId(Long userId);

    /**
     * Find user-role associations by role ID
     *
     * @param roleId Role ID
     * @return List of user-role associations
     */
    List<SysUserRole> findByRoleId(Long roleId);

    /**
     * Check if user has specific role
     *
     * @param userId User ID
     * @param roleId Role ID
     * @return true if association exists
     */
    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    /**
     * Delete user-role association
     *
     * @param userId User ID
     * @param roleId Role ID
     */
    @Modifying
    @Query("DELETE FROM SysUserRole sur WHERE sur.userId = :userId AND sur.roleId = :roleId")
    void deleteByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * Delete all roles for a user
     *
     * @param userId User ID
     */
    @Modifying
    @Query("DELETE FROM SysUserRole sur WHERE sur.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * Delete all users for a role
     *
     * @param roleId Role ID
     */
    @Modifying
    @Query("DELETE FROM SysUserRole sur WHERE sur.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * Get all role IDs for a user
     *
     * @param userId User ID
     * @return Set of role IDs
     */
    @Query("SELECT sur.roleId FROM SysUserRole sur WHERE sur.userId = :userId")
    Set<Long> findRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * Get all user IDs for a role
     *
     * @param roleId Role ID
     * @return Set of user IDs
     */
    @Query("SELECT sur.userId FROM SysUserRole sur WHERE sur.roleId = :roleId")
    Set<Long> findUserIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * Count users with specific role
     *
     * @param roleId Role ID
     * @return Number of users
     */
    long countByRoleId(Long roleId);

    /**
     * Count roles for specific user
     *
     * @param userId User ID
     * @return Number of roles
     */
    long countByUserId(Long userId);

    /**
     * Find users with multiple roles (batch query)
     *
     * @param userIds Set of user IDs
     * @return List of user-role associations
     */
    List<SysUserRole> findByUserIdIn(Set<Long> userIds);

    /**
     * Find all user-role associations with role details (for eager loading)
     *
     * @param userId User ID
     * @return List of user-role associations with role entity
     */
    @Query("SELECT sur FROM SysUserRole sur JOIN FETCH sur.role WHERE sur.userId = :userId")
    List<SysUserRole> findByUserIdWithRole(@Param("userId") Long userId);
}

package com.wms.system.repository;

import com.wms.system.entity.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * System Role Repository
 *
 * Data access interface for sys_role table.
 * Inherits JpaRepository for automatic CRUD methods.
 *
 * Auto-implemented Methods:
 * - save(SysRole role): Save or update role
 * - findById(Long id): Find role by ID
 * - findAll(): Find all roles
 * - deleteById(Long id): Delete role by ID
 * - count(): Count total roles
 *
 * Custom Query Methods:
 * - Query methods following Spring Data JPA naming conventions
 * - Complex queries using @Query annotation
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Repository
public interface SysRoleRepository extends JpaRepository<SysRole, Long> {

    /**
     * Find role by role code
     *
     * @param roleCode Role code (e.g., SUPER_ADMIN, CHAIRMAN)
     * @return Optional<SysRole>
     */
    Optional<SysRole> findByRoleCode(String roleCode);

    /**
     * Check if role code exists
     *
     * @param roleCode Role code
     * @return true if exists
     */
    boolean existsByRoleCode(String roleCode);

    /**
     * Find roles by role type
     *
     * @param roleType Role type (SYSTEM or CUSTOM)
     * @return List of roles
     */
    List<SysRole> findByRoleType(String roleType);

    /**
     * Find roles by status
     *
     * @param status Role status (ACTIVE or DISABLED)
     * @return List of roles
     */
    List<SysRole> findByStatus(String status);

    /**
     * Find roles by type and status
     *
     * @param roleType Role type
     * @param status Role status
     * @return List of roles
     */
    List<SysRole> findByRoleTypeAndStatus(String roleType, String status);

    /**
     * Find all active roles
     *
     * @return List of active roles
     */
    default List<SysRole> findAllActive() {
        return findByStatus("ACTIVE");
    }

    /**
     * Find all system predefined roles
     *
     * @return List of system roles
     */
    default List<SysRole> findAllSystemRoles() {
        return findByRoleType("SYSTEM");
    }

    /**
     * Find roles by ID set (for batch queries)
     *
     * @param roleIds Set of role IDs
     * @return List of roles
     */
    List<SysRole> findByIdIn(Set<Long> roleIds);

    /**
     * Find roles ordered by sort order
     *
     * @return List of roles ordered by sort_order ASC
     */
    List<SysRole> findAllByOrderBySortOrderAsc();

    /**
     * Search roles by name (fuzzy match)
     *
     * @param keyword Search keyword
     * @return List of matching roles
     */
    List<SysRole> findByRoleNameContaining(String keyword);
}

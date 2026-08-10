package com.wms.system.service;

import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysUserRole;
import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map;

/**
 * User-Role Assignment Service
 *
 * Manages many-to-many relationships between users and roles.
 *
 * Key Features:
 * - Assign roles to users
 * - Remove roles from users
 * - Query user's roles
 * - Query role's users
 * - Batch operations
 * - Cache invalidation after changes
 *
 * Business Rules:
 * - One user can have multiple roles
 * - Role assignment triggers cache eviction
 * - Cannot assign same role twice to same user
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRoleService {

    private final SysUserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final PermissionCacheService cacheService;
    private final SecurityVersionService securityVersionService;

    /**
     * Assign role to user
     *
     * @param userId User ID
     * @param roleId Role ID
     * @param assignedBy Administrator ID who made the assignment
     * @throws IllegalArgumentException if user or role not found
     */
    @Transactional
    public void assignRoleToUser(Long userId, Long roleId, Long assignedBy) {
        log.info("Assigning role {} to user {} by admin {}", roleId, userId, assignedBy);

        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Validate role exists
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));
        requireAssignable(role);

        // Check if already assigned
        if (userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            log.warn("Role {} already assigned to user {}", roleId, userId);
            return;
        }

        // Create assignment
        SysUserRole userRole = SysUserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .assignedBy(assignedBy)
                .build();

        userRoleRepository.save(userRole);

        securityVersionService.bumpForUser(userId);

        // Evict cache
        cacheService.onUserRoleAssigned(userId);

        log.info("Role assigned successfully: role={}, user={}", roleId, userId);
    }

    /**
     * Remove role from user
     *
     * @param userId User ID
     * @param roleId Role ID
     */
    @Transactional
    public void removeRoleFromUser(Long userId, Long roleId) {
        log.info("Removing role {} from user {}", roleId, userId);

        userRoleRepository.deleteByUserIdAndRoleId(userId, roleId);

        securityVersionService.bumpForUser(userId);

        // Evict cache
        cacheService.onUserRoleRemoved(userId);

        log.info("Role removed successfully: role={}, user={}", roleId, userId);
    }

    /**
     * Batch assign roles to user
     *
     * Replaces all existing roles with new set.
     *
     * @param userId User ID
     * @param roleIds Set of role IDs
     * @param assignedBy Administrator ID
     */
    @Transactional
    public void assignRolesToUser(Long userId, Set<Long> roleIds, Long assignedBy) {
        log.info("Batch assigning {} roles to user {}", roleIds.size(), userId);

        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<SysRole> roles = roleRepository.findByIdIn(roleIds);
        if (roles.size() != roleIds.size()) {
            Set<Long> foundRoleIds = roles.stream().map(SysRole::getId).collect(Collectors.toSet());
            Set<Long> missingRoleIds = roleIds.stream()
                    .filter(roleId -> !foundRoleIds.contains(roleId))
                    .collect(Collectors.toSet());
            throw new BusinessException(ErrorKeys.ROLE_NOT_FOUND, Map.of("roleIds", missingRoleIds));
        }
        roles.forEach(this::requireAssignable);

        // Remove all existing roles only after the complete replacement has been validated.
        userRoleRepository.deleteByUserId(userId);

        // Assign new roles
        for (SysRole role : roles) {
            SysUserRole userRole = SysUserRole.builder()
                    .userId(userId)
                    .roleId(role.getId())
                    .assignedBy(assignedBy)
                    .build();

            userRoleRepository.save(userRole);
        }

        securityVersionService.bumpForUser(userId);

        // Evict cache
        cacheService.onUserRoleAssigned(userId);

        log.info("Batch role assignment completed: {} roles assigned to user {}", roleIds.size(), userId);
    }

    private void requireAssignable(SysRole role) {
        if (!role.isAssignableToUsers()) {
            throw new BusinessException(ErrorKeys.ROLE_PACKAGE_NOT_ASSIGNABLE_TO_USER, Map.of(
                    "roleId", role.getId(),
                    "roleCode", role.getRoleCode(),
                    "status", role.getStatus(),
                    "reviewStatus", role.getReviewStatus()
            ));
        }
    }

    /**
     * Get role IDs for a user
     *
     * @param userId User ID
     * @return Set of role IDs
     */
    public Set<Long> getUserRoleIds(Long userId) {
        return userRoleRepository.findRoleIdsByUserId(userId);
    }

    /**
     * Get roles for a user (with details)
     *
     * @param userId User ID
     * @return List of roles
     */
    public List<SysRole> getUserRoles(Long userId) {
        Set<Long> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }

        return roleRepository.findByIdIn(roleIds);
    }

    /**
     * Get users for a role
     *
     * @param roleId Role ID
     * @return Set of user IDs
     */
    public Set<Long> getRoleUserIds(Long roleId) {
        return userRoleRepository.findUserIdsByRoleId(roleId);
    }

    /**
     * Get users for a role (with details)
     *
     * @param roleId Role ID
     * @return List of users
     */
    public List<User> getRoleUsers(Long roleId) {
        Set<Long> userIds = userRoleRepository.findUserIdsByRoleId(roleId);
        if (userIds.isEmpty()) {
            return List.of();
        }

        return userRepository.findAllById(userIds);
    }

    /**
     * Check if user has specific role
     *
     * @param userId User ID
     * @param roleId Role ID
     * @return true if user has the role
     */
    public boolean userHasRole(Long userId, Long roleId) {
        return userRoleRepository.existsByUserIdAndRoleId(userId, roleId);
    }

    /**
     * Check if user has specific role code
     *
     * @param userId User ID
     * @param roleCode Role code
     * @return true if user has the role
     */
    public boolean userHasRoleCode(Long userId, String roleCode) {
        Set<Long> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return false;
        }

        List<SysRole> roles = roleRepository.findByIdIn(roleIds);
        return roles.stream().anyMatch(role -> role.getRoleCode().equals(roleCode));
    }

    /**
     * Get user count for a role
     *
     * @param roleId Role ID
     * @return Number of users with this role
     */
    public long getUserCountForRole(Long roleId) {
        return userRoleRepository.countByRoleId(roleId);
    }
}

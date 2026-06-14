package com.wms.system.controller;

import com.wms.system.dto.*;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.PermissionCacheService;
import com.wms.system.service.UserManagementService;
import com.wms.system.service.UserRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User Management Controller
 *
 * Provides CRUD operations for user management (SUPER_ADMIN only).
 *
 * API Endpoints:
 * - GET /api/users: List all users with their assigned roles
 * - POST /api/users: Create new user with role assignments
 * - PUT /api/users/{id}: Update user information
 * - DELETE /api/users/{id}: Delete user
 * - POST /api/users/{id}/roles: Batch assign roles to user
 * - DELETE /api/users/{id}/roles/{roleId}: Remove single role from user
 *
 * Security:
 * - All endpoints require SUPER_ADMIN role
 * - Password is never returned in responses
 * - Prevents deletion of last SUPER_ADMIN (optional protection)
 * - Requires at least one role per user
 *
 * Multi-Role System (v3.3+):
 * - Users can have multiple roles assigned
 * - Role assignments managed via sys_user_role table
 * - Default role can be set/updated
 * - Permission cache is invalidated after role changes
 *
 * @author WMS Team
 * @since 2026-01-20
 * @version 3.3 (Multi-Role RBAC System)
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController {

    private final UserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final UserRoleService userRoleService;
    private final PasswordEncoder passwordEncoder;
    private final PermissionCacheService cacheService;
    private final UserManagementService userManagementService;

    /**
     * ⭐ Get All Users
     *
     * Returns list of all users with their assigned roles.
     *
     * API Endpoint:
     * GET /api/users
     *
     * Success Response (200 OK):
     * <pre>
     * [
     *   {
     *     "id": 1,
     *     "username": "admin",
     *     "displayName": "System Administrator",
     *     "enabled": true,
     *     "roleCodes": ["SUPER_ADMIN"],
     *     "roleNames": ["超级管理员"],
     *     "defaultRoleCode": "SUPER_ADMIN",
     *     "createdAt": "2026-01-10T10:00:00",
     *     "updatedAt": "2026-01-20T10:00:00",
     *     "remark": "Auto-created admin user"
     *   }
     * ]
     * </pre>
     *
     * @return List of users with roles
     */
    @GetMapping
    public ResponseEntity<List<UserWithRolesDTO>> getAllUsers() {
        log.info("Fetching all users with roles");

        List<User> users = userRepository.findAll();

        List<UserWithRolesDTO> userDTOs = users.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());

        log.info("Fetched {} users", userDTOs.size());

        return ResponseEntity.ok(userDTOs);
    }

    /**
     * ⭐ Create New User
     *
     * Creates a new user account with initial role assignments.
     *
     * API Endpoint:
     * POST /api/users
     *
     * Request Body:
     * <pre>
     * {
     *   "username": "new_employee",
     *   "password": "Welcome2026!",
     *   "displayName": "New Employee",
     *   "roleIds": [3, 5],
     *   "enabled": true,
     *   "remark": "Hired in January 2026"
     * }
     * </pre>
     *
     * Success Response (201 Created):
     * Returns UserWithRolesDTO of created user
     *
     * Error Responses:
     * - 409: USER_ALREADY_EXISTS - Username already taken
     * - 404: ROLE_NOT_FOUND - Invalid role ID in roleIds
     *
     * @param request Create user request
     * @return Created user with roles
     */
    @PostMapping
    public ResponseEntity<UserWithRolesDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Creating new user: username={}", request.getUsername());

        // 1. Verify username uniqueness
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Username already exists: username={}", request.getUsername());
            throw new BusinessException(
                ErrorKeys.USER_ALREADY_EXISTS,
                Map.of("username", request.getUsername())
            );
        }

        // 2. Verify all role IDs exist and are active
        List<SysRole> rolesToAssign = request.getRoleIds().stream()
            .map(roleId -> roleRepository.findById(roleId)
                .orElseThrow(() -> {
                    log.warn("Role not found: roleId={}", roleId);
                    throw new BusinessException(
                        ErrorKeys.ROLE_NOT_FOUND,
                        Map.of("roleId", roleId)
                    );
                }))
            .collect(Collectors.toList());

        // 3. BCrypt encode password
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 4. Create user entity
        User user = User.builder()
            .username(request.getUsername())
            .password(encodedPassword)
            .displayName(request.getDisplayName())
            .enabled(request.getEnabled())
            .remark(request.getRemark())
            .defaultRoleId(request.getRoleIds().get(0))  // First role as default
            .build();

        // 5. Save user
        user = userRepository.save(user);

        log.info("User created: userId={}, username={}", user.getId(), user.getUsername());

        // 6. Assign roles to user
        // Get admin ID from SecurityContext for assigned_by field
        Long adminId = user.getId(); // Using newly created user as assigned_by for bootstrap
        userRoleService.assignRolesToUser(user.getId(), new HashSet<>(request.getRoleIds()), adminId);

        log.info("Roles assigned to user: userId={}, roleCount={}", user.getId(), request.getRoleIds().size());

        // 7. Convert to DTO and return
        UserWithRolesDTO response = convertToDTO(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * ⭐ Update User
     *
     * Updates user information (display name, enabled status, default role, remark).
     *
     * API Endpoint:
     * PUT /api/users/{id}
     *
     * Request Body:
     * <pre>
     * {
     *   "displayName": "Updated Name",
     *   "enabled": false,
     *   "defaultRoleId": 5,
     *   "remark": "Account temporarily disabled"
     * }
     * </pre>
     *
     * Success Response (200 OK):
     * Returns updated UserWithRolesDTO
     *
     * Error Responses:
     * - 404: USER_NOT_FOUND - User does not exist
     * - 404: ROLE_NOT_FOUND - defaultRoleId is invalid
     * - 403: ROLE_NOT_ASSIGNED - defaultRoleId not assigned to user
     *
     * @param id User ID
     * @param request Update request
     * @return Updated user
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserWithRolesDTO> updateUser(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserRequest request
    ) {
        log.info("Updating user: userId={}", id);

        // 1. Load user
        User user = userRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("User not found: userId={}", id);
                throw new BusinessException(
                    ErrorKeys.USER_NOT_FOUND,
                    Map.of("userId", id)
                );
            });

        // 2. Update fields (only if provided)
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }

        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }

        if (request.getRemark() != null) {
            user.setRemark(request.getRemark());
        }

        if (request.getDefaultRoleId() != null) {
            // Verify user has this role assigned
            SysRole role = roleRepository.findById(request.getDefaultRoleId())
                .orElseThrow(() -> {
                    log.warn("Default role not found: roleId={}", request.getDefaultRoleId());
                    throw new BusinessException(
                        ErrorKeys.ROLE_NOT_FOUND,
                        Map.of("roleId", request.getDefaultRoleId())
                    );
                });

            boolean hasRole = userRoleService.userHasRole(id, request.getDefaultRoleId());
            if (!hasRole) {
                log.warn("User does not have requested default role: userId={}, roleId={}",
                    id, request.getDefaultRoleId());
                throw new BusinessException(
                    ErrorKeys.ROLE_NOT_ASSIGNED,
                    Map.of(
                        "roleId", role.getId(),
                        "roleCode", role.getRoleCode(),
                        "userId", id,
                        "username", user.getUsername()
                    )
                );
            }

            user.setDefaultRoleId(request.getDefaultRoleId());
        }

        // 3. Save updates
        user = userRepository.save(user);

        log.info("User updated: userId={}, username={}", user.getId(), user.getUsername());

        // 4. Clear permission cache
        cacheService.onUserUpdated(id);

        // 5. Convert to DTO and return
        UserWithRolesDTO response = convertToDTO(user);

        return ResponseEntity.ok(response);
    }

    /**
     * ⭐ Delete User
     *
     * Deletes a user account and all role assignments.
     *
     * API Endpoint:
     * DELETE /api/users/{id}
     *
     * Success Response (204 No Content)
     *
     * Error Responses:
     * - 404: USER_NOT_FOUND - User does not exist
     *
     * @param id User ID
     * @return No content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
        @PathVariable Long id,
        Authentication authentication
    ) {
        log.info("Deleting user: userId={}", id);
        Long operatorId = AuthUserResolver.resolveUserId(authentication);
        if (operatorId == null || operatorId == 0L) {
            String operatorUsername = AuthUserResolver.resolveUsername(authentication);
            operatorId = userRepository.findByUsername(operatorUsername)
                .map(User::getId)
                .orElse(0L);
        }
        userManagementService.deleteUser(id, operatorId);
        return ResponseEntity.noContent().build();
    }

    /**
     * ⭐ Assign Roles to User (Batch)
     *
     * Replaces all existing role assignments with new ones.
     *
     * API Endpoint:
     * POST /api/users/{id}/roles
     *
     * Request Body:
     * <pre>
     * {
     *   "roleIds": [3, 5, 7]
     * }
     * </pre>
     *
     * Success Response (200 OK):
     * Returns updated UserWithRolesDTO
     *
     * Error Responses:
     * - 404: USER_NOT_FOUND - User does not exist
     * - 404: ROLE_NOT_FOUND - Invalid role ID in roleIds
     *
     * @param id User ID
     * @param request Role assignment request
     * @return Updated user
     */
    @PostMapping("/{id}/roles")
    public ResponseEntity<UserWithRolesDTO> assignRoles(
        @PathVariable Long id,
        @Valid @RequestBody AssignRolesRequest request
    ) {
        log.info("Assigning roles to user: userId={}, roleCount={}", id, request.getRoleIds().size());

        // 1. Verify user exists
        User user = userRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("User not found: userId={}", id);
                throw new BusinessException(
                    ErrorKeys.USER_NOT_FOUND,
                    Map.of("userId", id)
                );
            });

        // 2. Verify all role IDs exist
        List<SysRole> rolesToAssign = request.getRoleIds().stream()
            .map(roleId -> roleRepository.findById(roleId)
                .orElseThrow(() -> {
                    log.warn("Role not found: roleId={}", roleId);
                    throw new BusinessException(
                        ErrorKeys.ROLE_NOT_FOUND,
                        Map.of("roleId", roleId)
                    );
                }))
            .collect(Collectors.toList());

        // 3. Get admin ID from SecurityContext (not implemented, using user id)
        Long adminId = id;

        // 4. Assign roles (replaces existing assignments)
        userRoleService.assignRolesToUser(id, new HashSet<>(request.getRoleIds()), adminId);

        log.info("Roles assigned successfully: userId={}, roleCount={}", id, request.getRoleIds().size());

        // 5. Update default role if current default is not in new roles
        if (user.getDefaultRoleId() == null || !request.getRoleIds().contains(user.getDefaultRoleId())) {
            user.setDefaultRoleId(request.getRoleIds().get(0));
            userRepository.save(user);
            log.debug("Updated default role: userId={}, defaultRoleId={}", id, user.getDefaultRoleId());
        }

        // 6. Clear permission cache
        cacheService.onUserRoleAssigned(id);

        // 7. Convert to DTO and return
        UserWithRolesDTO response = convertToDTO(user);

        return ResponseEntity.ok(response);
    }

    /**
     * ⭐ Remove Role from User
     *
     * Removes a single role assignment from user.
     *
     * API Endpoint:
     * DELETE /api/users/{id}/roles/{roleId}
     *
     * Success Response (204 No Content)
     *
     * Error Responses:
     * - 404: USER_NOT_FOUND - User does not exist
     * - 404: ROLE_NOT_FOUND - Role does not exist
     * - 403: ROLE_NOT_ASSIGNED - User does not have this role
     * - 403: OPERATION_NOT_ALLOWED - Cannot remove last role
     *
     * @param id User ID
     * @param roleId Role ID to remove
     * @return No content
     */
    @DeleteMapping("/{id}/roles/{roleId}")
    public ResponseEntity<Void> removeRole(
        @PathVariable Long id,
        @PathVariable Long roleId
    ) {
        log.info("Removing role from user: userId={}, roleId={}", id, roleId);

        // 1. Verify user exists
        User user = userRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("User not found: userId={}", id);
                throw new BusinessException(
                    ErrorKeys.USER_NOT_FOUND,
                    Map.of("userId", id)
                );
            });

        // 2. Verify role exists
        SysRole role = roleRepository.findById(roleId)
            .orElseThrow(() -> {
                log.warn("Role not found: roleId={}", roleId);
                throw new BusinessException(
                    ErrorKeys.ROLE_NOT_FOUND,
                    Map.of("roleId", roleId)
                );
            });

        // 3. Verify user has this role
        boolean hasRole = userRoleService.userHasRole(id, roleId);
        if (!hasRole) {
            log.warn("User does not have role: userId={}, roleId={}", id, roleId);
            throw new BusinessException(
                ErrorKeys.ROLE_NOT_ASSIGNED,
                Map.of(
                    "roleId", roleId,
                    "roleCode", role.getRoleCode(),
                    "userId", id,
                    "username", user.getUsername()
                )
            );
        }

        // 4. Verify user has at least 2 roles (cannot remove last role)
        List<SysRole> userRoles = userRoleService.getUserRoles(id);
        if (userRoles.size() <= 1) {
            log.warn("Cannot remove last role: userId={}, roleId={}", id, roleId);
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of(
                    "operation", "Remove role",
                    "reason", "User must have at least one role"
                )
            );
        }

        // 5. Remove role
        userRoleService.removeRoleFromUser(id, roleId);

        log.info("Role removed successfully: userId={}, roleId={}", id, roleId);

        // 6. Update default role if we just removed it
        if (user.getDefaultRoleId() != null && user.getDefaultRoleId().equals(roleId)) {
            List<SysRole> remainingRoles = userRoleService.getUserRoles(id);
            if (!remainingRoles.isEmpty()) {
                user.setDefaultRoleId(remainingRoles.get(0).getId());
                userRepository.save(user);
                log.debug("Updated default role after removal: userId={}, newDefaultRoleId={}",
                    id, user.getDefaultRoleId());
            }
        }

        // 7. Clear permission cache
        cacheService.onUserRoleRemoved(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Convert User Entity to DTO with Roles
     *
     * @param user User entity
     * @return UserWithRolesDTO
     */
    private UserWithRolesDTO convertToDTO(User user) {
        // Load user roles
        List<SysRole> userRoles = userRoleService.getUserRoles(user.getId());

        // Extract role codes and names
        List<String> roleCodes = userRoles.stream()
            .map(SysRole::getRoleCode)
            .collect(Collectors.toList());

        List<String> roleNames = userRoles.stream()
            .map(SysRole::getRoleName)
            .collect(Collectors.toList());

        // Get default role code
        String defaultRoleCode = null;
        if (user.getDefaultRoleId() != null) {
            defaultRoleCode = roleRepository.findById(user.getDefaultRoleId())
                .map(SysRole::getRoleCode)
                .orElse(null);
        }

        return UserWithRolesDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .displayName(user.getDisplayName())
            .enabled(user.getEnabled())
            .roleCodes(roleCodes)
            .roleNames(roleNames)
            .defaultRoleCode(defaultRoleCode)
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .remark(user.getRemark())
            .build();
    }
}

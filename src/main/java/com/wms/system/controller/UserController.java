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
import com.wms.system.service.SecurityVersionService;
import com.wms.system.service.UserManagementService;
import com.wms.system.service.UserRoleService;
import com.wms.system.service.UserWarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User Management Controller
 *
 * Provides CRUD operations for user management to SUPER_ADMIN and the
 * restricted SECURITY_ADMIN identity.
 *
 * API Endpoints:
 * - GET /api/users: List all users with their assigned roles
 * - POST /api/users: Create new user with role assignments
 * - PUT /api/users/{id}: Update user information
 * - DELETE /api/users/{id}: Delete user
 * - POST /api/users/{id}/roles: Batch assign roles to user
 * - DELETE /api/users/{id}/roles/{roleId}: Remove single role from user
 * - PUT /api/users/me/password: Change own password (any authenticated user, P0.5)
 * - POST /api/users/{id}/reset-password: Admin reset to temporary password (P0.5)
 *
 * Security:
 * - IAM endpoints require SUPER_ADMIN or SECURITY_ADMIN, except /me/password
 *   which is available to any authenticated user
 * - SECURITY_ADMIN cannot grant or modify protected administrator identities
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
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SECURITY_ADMIN')")
public class UserController {

    private final UserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final UserRoleService userRoleService;
    private final PasswordEncoder passwordEncoder;
    private final PermissionCacheService cacheService;
    private final UserManagementService userManagementService;
    private final SecurityVersionService securityVersionService;
    private final UserWarehouseService userWarehouseService;

    /**
     * 猸?Get All Users
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
     *     "roleNames": ["瓒呯骇绠＄悊鍛?],
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

    /** Active warehouses available for WAREHOUSE_STAFF assignment. */
    @GetMapping("/assignable-warehouses")
    public ResponseEntity<List<AssignableWarehouseDTO>> getAssignableWarehouses() {
        return ResponseEntity.ok(userWarehouseService.listAssignableWarehouses());
    }

    /**
     * 猸?Create New User
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
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<UserWithRolesDTO> createUser(
        @Valid @RequestBody CreateUserRequest request,
        Authentication authentication
    ) {
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
            .peek(role -> {
                if (!role.isAssignableToUsers()) {
                    throw new BusinessException(
                        ErrorKeys.ROLE_PACKAGE_NOT_ASSIGNABLE_TO_USER,
                        Map.of(
                            "roleId", role.getId(),
                            "roleCode", role.getRoleCode(),
                            "status", role.getStatus(),
                            "reviewStatus", role.getReviewStatus()
                        )
                    );
                }
            })
            .collect(Collectors.toList());
        userManagementService.validateRoleAssignment(
            rolesToAssign,
            AuthUserResolver.resolveCurrentRole(authentication)
        );
        userWarehouseService.validateSelection(rolesToAssign, request.getWarehouseIds());

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
        Long adminId = authentication == null ? 0L : resolveOperatorId(authentication);
        if (adminId == null || adminId == 0L) {
            adminId = user.getId();
        }
        userRoleService.assignRolesToUser(user.getId(), new HashSet<>(request.getRoleIds()), adminId);
        userWarehouseService.replaceAssignments(
            user.getId(),
            rolesToAssign,
            request.getWarehouseIds(),
            adminId
        );

        log.info("Roles assigned to user: userId={}, roleCount={}", user.getId(), request.getRoleIds().size());

        // 7. Convert to DTO and return
        UserWithRolesDTO response = convertToDTO(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    ResponseEntity<UserWithRolesDTO> createUser(CreateUserRequest request) {
        return createUser(request, null);
    }

    /**
     * 猸?Update User
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
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<UserWithRolesDTO> updateUser(
        @PathVariable("id") Long id,
        @Valid @RequestBody UpdateUserRequest request,
        Authentication authentication
    ) {
        log.info("Updating user: userId={}", id);

        userManagementService.validateProfileChange(
            id,
            authentication == null ? 0L : resolveOperatorId(authentication),
            request.getEnabled(),
            AuthUserResolver.resolveCurrentRole(authentication)
        );

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

        boolean enabledChanged = request.getEnabled() != null
            && !request.getEnabled().equals(user.getEnabled());
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

        if (enabledChanged) {
            securityVersionService.bump(user);
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

    ResponseEntity<UserWithRolesDTO> updateUser(Long id, UpdateUserRequest request) {
        return updateUser(id, request, null);
    }

    /**
     * 猸?Delete User
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
        @PathVariable("id") Long id,
        Authentication authentication
    ) {
        log.info("Deleting user: userId={}", id);
        Long operatorId = resolveOperatorId(authentication);
        userManagementService.deleteUser(
            id,
            operatorId,
            AuthUserResolver.resolveCurrentRole(authentication)
        );
        return ResponseEntity.noContent().build();
    }

    /**
     * 猸?Assign Roles to User (Batch)
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
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<UserWithRolesDTO> assignRoles(
        @PathVariable("id") Long id,
        @Valid @RequestBody AssignRolesRequest request,
        Authentication authentication
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
            .peek(role -> {
                if (!role.isAssignableToUsers()) {
                    throw new BusinessException(
                        ErrorKeys.ROLE_PACKAGE_NOT_ASSIGNABLE_TO_USER,
                        Map.of(
                            "roleId", role.getId(),
                            "roleCode", role.getRoleCode(),
                            "status", role.getStatus(),
                            "reviewStatus", role.getReviewStatus()
                        )
                    );
                }
            })
            .collect(Collectors.toList());

        if (request.getDefaultRoleId() != null
            && !request.getRoleIds().contains(request.getDefaultRoleId())) {
            throw new BusinessException(
                ErrorKeys.ROLE_NOT_ASSIGNED,
                Map.of(
                    "roleId", request.getDefaultRoleId(),
                    "userId", id,
                    "reason", "Default role must be included in roleIds"
                )
            );
        }

        // 3. Resolve the real operator and protect self/last-admin role changes.
        Long adminId = authentication == null ? 0L : resolveOperatorId(authentication);
        userManagementService.validateRoleReplacement(
            id,
            adminId,
            new HashSet<>(request.getRoleIds()),
            AuthUserResolver.resolveCurrentRole(authentication)
        );
        userWarehouseService.validateSelection(rolesToAssign, request.getWarehouseIds());
        if (adminId == null || adminId == 0L) {
            adminId = id;
        }

        // 4. Assign roles (replaces existing assignments)
        userRoleService.assignRolesToUser(id, new HashSet<>(request.getRoleIds()), adminId);
        userWarehouseService.replaceAssignments(
            id,
            rolesToAssign,
            request.getWarehouseIds(),
            adminId
        );

        log.info("Roles assigned successfully: userId={}, roleCount={}", id, request.getRoleIds().size());

        // 5. Update default role in the same transaction as role replacement.
        Long nextDefaultRoleId = request.getDefaultRoleId();
        if (nextDefaultRoleId == null) {
            nextDefaultRoleId = user.getDefaultRoleId() != null
                && request.getRoleIds().contains(user.getDefaultRoleId())
                ? user.getDefaultRoleId()
                : request.getRoleIds().get(0);
        }
        if (!nextDefaultRoleId.equals(user.getDefaultRoleId())) {
            user.setDefaultRoleId(nextDefaultRoleId);
            userRepository.save(user);
            log.debug("Updated default role: userId={}, defaultRoleId={}", id, user.getDefaultRoleId());
        }

        // 6. Clear permission cache
        cacheService.onUserRoleAssigned(id);

        // 7. Convert to DTO and return
        UserWithRolesDTO response = convertToDTO(user);

        return ResponseEntity.ok(response);
    }

    ResponseEntity<UserWithRolesDTO> assignRoles(Long id, AssignRolesRequest request) {
        return assignRoles(id, request, null);
    }

    /**
     * 猸?Remove Role from User
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
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Void> removeRole(
        @PathVariable("id") Long id,
        @PathVariable("roleId") Long roleId,
        Authentication authentication
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

        HashSet<Long> remainingRoleIds = userRoles.stream()
            .map(SysRole::getId)
            .filter(existingRoleId -> !existingRoleId.equals(roleId))
            .collect(Collectors.toCollection(HashSet::new));
        userManagementService.validateRoleReplacement(
            id,
            authentication == null ? 0L : resolveOperatorId(authentication),
            remainingRoleIds,
            AuthUserResolver.resolveCurrentRole(authentication)
        );

        // 5. Remove role
        userRoleService.removeRoleFromUser(id, roleId);
        if (UserWarehouseService.WAREHOUSE_STAFF.equals(role.getRoleCode())) {
            userWarehouseService.clearAssignments(id);
        }

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

    ResponseEntity<Void> removeRole(Long id, Long roleId) {
        return removeRole(id, roleId, null);
    }

    /**
     * ⭐ Change Own Password (P0.5)
     *
     * Allows the authenticated user to change their own password.
     * Available to ANY authenticated user (not just SUPER_ADMIN): the
     * method-level @PreAuthorize overrides the class-level IAM administrator rule.
     *
     * API Endpoint:
     * PUT /api/users/me/password
     *
     * Request Body:
     * <pre>
     * {
     *   "oldPassword": "current-password",
     *   "newPassword": "NewPassword2026"
     * }
     * </pre>
     *
     * Success Response (204 No Content)
     *
     * Error Responses:
     * - 400: PASSWORD_INCORRECT - Old password verification failed
     * - 400: PASSWORD_TOO_WEAK - New password violates the strength policy
     * - 400: PASSWORD_SAME_AS_OLD - New password equals the current one
     *
     * Side effect: clears must_change_password, lifting the temporary-password
     * restriction after an admin reset.
     *
     * @param request Old + new password
     * @param authentication Current authentication
     * @return No content
     */
    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changeMyPassword(
        @Valid @RequestBody ChangeMyPasswordRequest request,
        Authentication authentication
    ) {
        Long userId = resolveOperatorId(authentication);
        if (userId == 0L) {
            throw new BusinessException(
                ErrorKeys.USER_NOT_FOUND,
                Map.of("username", AuthUserResolver.resolveUsername(authentication))
            );
        }

        log.info("Password change requested: userId={}", userId);
        userManagementService.changeOwnPassword(userId, request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * ⭐ Reset User Password (P0.5, IAM administrators; protected targets restricted)
     *
     * Resets the target user's password to a generated temporary password and
     * flags the account must_change_password: until the user changes it via
     * PUT /api/users/me/password, all other APIs are blocked for that account.
     *
     * API Endpoint:
     * POST /api/users/{id}/reset-password
     *
     * Success Response (200 OK):
     * <pre>
     * {
     *   "userId": 5,
     *   "username": "employee",
     *   "temporaryPassword": "aB3kM9pQrs2x",
     *   "mustChangePassword": true
     * }
     * </pre>
     * The temporary password is shown ONCE here and never logged.
     *
     * Error Responses:
     * - 404: USER_NOT_FOUND - Target user does not exist
     * - 403: OPERATION_NOT_ALLOWED - Resetting your own password (use /me/password)
     *
     * @param id Target user ID
     * @param authentication Current authentication (operator)
     * @return Temporary password payload
     */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(
        @PathVariable("id") Long id,
        Authentication authentication
    ) {
        Long operatorId = resolveOperatorId(authentication);
        log.info("Password reset requested: targetUserId={}, operatorId={}", id, operatorId);
        return ResponseEntity.ok(userManagementService.resetPassword(
            id,
            operatorId,
            AuthUserResolver.resolveCurrentRole(authentication)
        ));
    }

    /**
     * Resolve the operator's user ID from the authentication, falling back to
     * a username lookup (same pattern as deleteUser).
     */
    private Long resolveOperatorId(Authentication authentication) {
        Long operatorId = AuthUserResolver.resolveUserId(authentication);
        if (operatorId == null || operatorId == 0L) {
            String operatorUsername = AuthUserResolver.resolveUsername(authentication);
            operatorId = userRepository.findByUsername(operatorUsername)
                .map(User::getId)
                .orElse(0L);
        }
        return operatorId;
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

        // Extract role IDs, codes and names
        List<Long> roleIds = userRoles.stream()
            .map(SysRole::getId)
            .collect(Collectors.toList());

        List<String> roleCodes = userRoles.stream()
            .map(SysRole::getRoleCode)
            .collect(Collectors.toList());

        List<String> roleNames = userRoles.stream()
            .map(SysRole::getRoleName)
            .collect(Collectors.toList());

        List<AssignableWarehouseDTO> assignedWarehouses =
            userWarehouseService.getAssignedWarehouses(user.getId());

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
            .roleIds(roleIds)
            .roleCodes(roleCodes)
            .roleNames(roleNames)
            .warehouseIds(assignedWarehouses.stream().map(AssignableWarehouseDTO::getId).toList())
            .warehouseCodes(assignedWarehouses.stream().map(AssignableWarehouseDTO::getCode).toList())
            .warehouseNames(assignedWarehouses.stream().map(AssignableWarehouseDTO::getName).toList())
            .defaultRoleCode(defaultRoleCode)
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .remark(user.getRemark())
            .build();
    }
}

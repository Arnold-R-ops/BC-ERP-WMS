package com.wms.system.service;

import com.wms.system.dto.ResetPasswordResponse;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.User;
import com.wms.system.entity.TenantSessionSecurityAudit;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.SysUserWarehouseRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.repository.TenantSessionSecurityAuditRepository;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Enterprise IAM lifecycle operations for user accounts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final String TENANT_ADMIN = "TENANT_ADMIN";
    private static final String SECURITY_ADMIN = "SECURITY_ADMIN";

    /**
     * Password strength policy (P0.5): 8-64 characters, at least one letter
     * and one digit. Kept deliberately simple; tighten here if policy evolves.
     */
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 64;
    private static final String PASSWORD_POLICY_DESCRIPTION =
        "8-64 characters with at least one letter and one digit";

    /**
     * URL-safe alphabet without look-alike characters (0/O, 1/l/I),
     * same convention as the admin bootstrap password generator.
     */
    private static final String TEMP_PASSWORD_ALPHABET =
        "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final SysUserWarehouseRepository userWarehouseRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionCacheService cacheService;
    private final SecurityVersionService securityVersionService;
    private final TenantSessionSecurityAuditRepository sessionSecurityAuditRepository;

    /** Revoke every tenant JWT issued to the caller after verifying the password. */
    @Transactional(rollbackFor = Exception.class)
    public void revokeOwnSessions(Long userId, String currentPassword) {
        Long companyId = CompanyScope.currentCompanyId();
        User user = userRepository.findByIdAndCompanyId(userId, companyId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.USER_NOT_FOUND, Map.of("userId", userId)));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException(
                ErrorKeys.PASSWORD_INCORRECT, Map.of("userId", userId));
        }

        securityVersionService.bump(user);
        userRepository.save(user);
        sessionSecurityAuditRepository.save(TenantSessionSecurityAudit.builder()
            .companyId(companyId)
            .action("SELF_REVOKE_ALL_SESSIONS")
            .operatorId(user.getId())
            .operatorUsername(user.getUsername())
            .targetUserId(user.getId())
            .targetUsername(user.getUsername())
            .reason("SELF_SERVICE")
            .result("SUCCESS")
            .build());
        cacheService.onUserUpdated(userId);
        log.info("All tenant sessions revoked by account owner: userId={}", userId);
    }

    /** Revoke every tenant JWT issued to a user in the current tenant. */
    @Transactional(rollbackFor = Exception.class)
    public void revokeUserSessions(
        Long targetUserId,
        Long operatorId,
        String operatorUsername,
        String operatorRoleCode,
        String reason
    ) {
        Long companyId = CompanyScope.currentCompanyId();
        User target = userRepository.findByIdAndCompanyId(targetUserId, companyId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.USER_NOT_FOUND, Map.of("userId", targetUserId)));
        if (operatorId != null && operatorId.equals(targetUserId)) {
            throw new BusinessException(ErrorKeys.OPERATION_NOT_ALLOWED, Map.of(
                "operation", "Revoke all sessions",
                "reason", "Use the self-service operation and verify the current password"
            ));
        }
        protectPrivilegedTargetForSecurityAdmin(
            target, operatorRoleCode, "Revoke all sessions");

        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.length() < 5 || normalizedReason.length() > 500) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "reason"));
        }

        securityVersionService.bump(target);
        userRepository.save(target);
        sessionSecurityAuditRepository.save(TenantSessionSecurityAudit.builder()
            .companyId(companyId)
            .action("ADMIN_REVOKE_ALL_SESSIONS")
            .operatorId(operatorId)
            .operatorUsername(operatorUsername)
            .targetUserId(target.getId())
            .targetUsername(target.getUsername())
            .reason(normalizedReason)
            .result("SUCCESS")
            .build());
        cacheService.onUserUpdated(targetUserId);
        log.warn("All tenant sessions revoked by administrator: targetUserId={}, operatorId={}",
            targetUserId, operatorId);
    }

    /**
     * Logically delete a user while preserving historical references.
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId, Long operatorId) {
        deleteUser(userId, operatorId, TENANT_ADMIN);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId, Long operatorId, String operatorRoleCode) {
        Long companyId = CompanyScope.currentCompanyId();
        User target = userRepository.findByIdAndCompanyId(userId, companyId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.USER_NOT_FOUND,
                Map.of("userId", userId)
            ));

        if (operatorId != null && operatorId > 0 && operatorId.equals(userId)) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of(
                    "operation", "Delete user",
                    "reason", "Administrators cannot delete their own account"
                )
            );
        }

        protectPrivilegedTargetForSecurityAdmin(target, operatorRoleCode, "Delete user");
        protectLastTenantAdmin(target, "Delete user");

        String originalUsername = target.getUsername();
        String deletionKey = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        target.setUsername("deleted_" + userId + "_" + deletionKey);
        target.setDisplayName("Deleted User");
        target.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        target.setEnabled(false);
        target.setDefaultRoleId(null);
        target.setRemark("Logically deleted IAM account");
        target.setIsDeleted(true);
        target.setDeletedAt(LocalDateTime.now());
        target.setDeletedBy(operatorId);
        securityVersionService.bump(target);

        userRoleRepository.deleteByCompanyIdAndUserId(companyId, userId);
        userWarehouseRepository.deleteByCompanyIdAndUserId(companyId, userId);
        userRepository.save(target);

        evictSecurityStateAfterCommit(userId);
        log.info("User logically deleted: userId={}, originalUsername={}, operatorId={}",
            userId, originalUsername, operatorId);
    }

    /** Prevent profile changes that could lock the active administrator out. */
    @Transactional(readOnly = true)
    public void validateProfileChange(Long userId, Long operatorId, Boolean requestedEnabled) {
        validateProfileChange(userId, operatorId, requestedEnabled, TENANT_ADMIN);
    }

    @Transactional(readOnly = true)
    public void validateProfileChange(
            Long userId,
            Long operatorId,
            Boolean requestedEnabled,
            String operatorRoleCode
    ) {
        User target = userRepository.findByIdAndCompanyId(
                userId, CompanyScope.currentCompanyId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.USER_NOT_FOUND,
                Map.of("userId", userId)
            ));

        protectPrivilegedTargetForSecurityAdmin(target, operatorRoleCode, "Update user");

        if (!Boolean.FALSE.equals(requestedEnabled)) {
            return;
        }

        if (operatorId != null && operatorId > 0 && operatorId.equals(userId)) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of(
                    "operation", "Disable user",
                    "reason", "Administrators cannot disable their own account"
                )
            );
        }

        protectLastTenantAdmin(target, "Disable user");
    }

    /** Prevent self role replacement and removal of the final TENANT_ADMIN role. */
    @Transactional(readOnly = true)
    public void validateRoleReplacement(Long userId, Long operatorId, Set<Long> requestedRoleIds) {
        validateRoleReplacement(userId, operatorId, requestedRoleIds, TENANT_ADMIN);
    }

    @Transactional(readOnly = true)
    public void validateRoleReplacement(
            Long userId,
            Long operatorId,
            Set<Long> requestedRoleIds,
            String operatorRoleCode
    ) {
        Long companyId = CompanyScope.currentCompanyId();
        User target = userRepository.findByIdAndCompanyId(userId, companyId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.USER_NOT_FOUND,
                Map.of("userId", userId)
            ));

        protectPrivilegedTargetForSecurityAdmin(target, operatorRoleCode, "Replace user roles");
        if (isSecurityAdmin(operatorRoleCode)) {
            List<SysRole> requestedRoles = roleRepository
                .findByCompanyIdAndIdIn(companyId, requestedRoleIds);
            if (requestedRoles.stream().anyMatch(SysRole::isPrivilegedRole)) {
                throw protectedIdentityOperation("Assign protected role");
            }
        }

        if (operatorId != null && operatorId > 0 && operatorId.equals(userId)) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of(
                    "operation", "Replace user roles",
                    "reason", "Use another TENANT_ADMIN account to change your role assignment"
                )
            );
        }

        SysRole tenantAdminRole = roleRepository
            .findByCompanyIdAndRoleCode(companyId, TENANT_ADMIN).orElse(null);
        if (tenantAdminRole == null
            || !userRoleRepository.existsByCompanyIdAndUserIdAndRoleId(
                companyId, target.getId(), tenantAdminRole.getId())
            || requestedRoleIds.contains(tenantAdminRole.getId())) {
            return;
        }

        protectLastTenantAdmin(target, "Remove TENANT_ADMIN role");
    }

    /** SECURITY_ADMIN may create ordinary accounts but cannot grant protected roles. */
    @Transactional(readOnly = true)
    public void validateRoleAssignment(List<SysRole> roles, String operatorRoleCode) {
        if (isSecurityAdmin(operatorRoleCode)
                && roles.stream().anyMatch(SysRole::isPrivilegedRole)) {
            throw protectedIdentityOperation("Assign protected role");
        }
    }

    /**
     * Change the caller's own password (P0.5).
     *
     * Verifies the old password, enforces the strength policy and clears the
     * must-change-password flag so a temporary password stops restricting the
     * account. Advancing security_version revokes every previously issued JWT.
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeOwnPassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findByIdAndCompanyId(
                userId, CompanyScope.currentCompanyId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.USER_NOT_FOUND,
                Map.of("userId", userId)
            ));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(
                ErrorKeys.PASSWORD_INCORRECT,
                Map.of("userId", userId)
            );
        }

        validatePasswordStrength(newPassword);

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException(
                ErrorKeys.PASSWORD_SAME_AS_OLD,
                Map.of("userId", userId)
            );
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        securityVersionService.bump(user);
        userRepository.save(user);

        log.info("Password changed by user: userId={}, username={}", userId, user.getUsername());
    }

    /**
     * Reset another user's password to a generated temporary one (P0.5).
     *
     * The temporary password is returned once in the response and never
     * logged. The target account is flagged must_change_password, which
     * restricts it to the change-password endpoint until the user sets
     * their own password.
     */
    @Transactional(rollbackFor = Exception.class)
    public ResetPasswordResponse resetPassword(Long targetUserId, Long operatorId) {
        return resetPassword(targetUserId, operatorId, TENANT_ADMIN);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResetPasswordResponse resetPassword(
            Long targetUserId,
            Long operatorId,
            String operatorRoleCode
    ) {
        User target = userRepository.findByIdAndCompanyId(
                targetUserId, CompanyScope.currentCompanyId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.USER_NOT_FOUND,
                Map.of("userId", targetUserId)
            ));

        protectPrivilegedTargetForSecurityAdmin(target, operatorRoleCode, "Reset password");

        if (operatorId != null && operatorId > 0 && operatorId.equals(targetUserId)) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of(
                    "operation", "Reset password",
                    "reason", "Use PUT /api/users/me/password to change your own password"
                )
            );
        }

        String temporaryPassword = generateTemporaryPassword();
        target.setPassword(passwordEncoder.encode(temporaryPassword));
        target.setMustChangePassword(true);
        securityVersionService.bump(target);
        userRepository.save(target);

        log.info("Password reset by administrator: targetUserId={}, targetUsername={}, operatorId={}",
            targetUserId, target.getUsername(), operatorId);

        return ResetPasswordResponse.builder()
            .userId(target.getId())
            .username(target.getUsername())
            .temporaryPassword(temporaryPassword)
            .mustChangePassword(true)
            .build();
    }

    private void protectPrivilegedTargetForSecurityAdmin(
            User target,
            String operatorRoleCode,
            String operation
    ) {
        if (!isSecurityAdmin(operatorRoleCode)) {
            return;
        }
        Long companyId = target.getCompanyId();
        Set<Long> targetRoleIds = userRoleRepository
            .findRoleIdsByCompanyIdAndUserId(companyId, target.getId());
        if (!targetRoleIds.isEmpty()
                && roleRepository.findByCompanyIdAndIdIn(companyId, targetRoleIds)
                    .stream().anyMatch(SysRole::isPrivilegedRole)) {
            throw protectedIdentityOperation(operation);
        }
    }

    private boolean isSecurityAdmin(String operatorRoleCode) {
        return SECURITY_ADMIN.equals(operatorRoleCode);
    }

    private BusinessException protectedIdentityOperation(String operation) {
        return new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of(
                        "operation", operation,
                        "reason", "SECURITY_ADMIN cannot grant or modify protected privileged identities"
                )
        );
    }

    private void validatePasswordStrength(String password) {
        boolean lengthOk = password != null
            && password.length() >= PASSWORD_MIN_LENGTH
            && password.length() <= PASSWORD_MAX_LENGTH;
        boolean hasLetter = lengthOk && password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = lengthOk && password.chars().anyMatch(Character::isDigit);

        if (!lengthOk || !hasLetter || !hasDigit) {
            throw new BusinessException(
                ErrorKeys.PASSWORD_TOO_WEAK,
                Map.of("policy", PASSWORD_POLICY_DESCRIPTION)
            );
        }
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_ALPHABET.charAt(SECURE_RANDOM.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    private void protectLastTenantAdmin(User target, String operation) {
        if (!Boolean.TRUE.equals(target.getEnabled())) {
            return;
        }

        Long companyId = target.getCompanyId();
        SysRole tenantAdminRole = roleRepository
            .findByCompanyIdAndRoleCode(companyId, TENANT_ADMIN).orElse(null);
        if (tenantAdminRole == null
            || !userRoleRepository.existsByCompanyIdAndUserIdAndRoleId(
                companyId, target.getId(), tenantAdminRole.getId())) {
            return;
        }

        long activeTenantAdmins = userRoleRepository
            .countActiveUsersByCompanyIdAndRoleCode(companyId, TENANT_ADMIN);
        if (activeTenantAdmins <= 1) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of(
                    "operation", operation,
                    "reason", "The last active TENANT_ADMIN account must remain enabled with its role"
                )
            );
        }
    }

    private void evictSecurityStateAfterCommit(Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cacheService.onUserDeleted(userId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cacheService.onUserDeleted(userId);
            }
        });
    }
}

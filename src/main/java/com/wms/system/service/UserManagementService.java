package com.wms.system.service;

import com.wms.system.dto.ResetPasswordResponse;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise IAM lifecycle operations for user accounts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

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
    private final PasswordEncoder passwordEncoder;
    private final PermissionCacheService cacheService;

    /**
     * Logically delete a user while preserving historical references.
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId, Long operatorId) {
        User target = userRepository.findById(userId)
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

        protectLastSuperAdmin(target);

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

        userRoleRepository.deleteByUserId(userId);
        userRepository.save(target);

        evictSecurityStateAfterCommit(userId);
        log.info("User logically deleted: userId={}, originalUsername={}, operatorId={}",
            userId, originalUsername, operatorId);
    }

    /**
     * Change the caller's own password (P0.5).
     *
     * Verifies the old password, enforces the strength policy and clears the
     * must-change-password flag so a temporary password stops restricting the
     * account. Existing JWTs stay valid until they expire (no revocation
     * mechanism yet, same as role switching).
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeOwnPassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
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
        User target = userRepository.findById(targetUserId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.USER_NOT_FOUND,
                Map.of("userId", targetUserId)
            ));

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

    private void protectLastSuperAdmin(User target) {
        SysRole superAdminRole = roleRepository.findByRoleCode(SUPER_ADMIN).orElse(null);
        if (superAdminRole == null
            || !userRoleRepository.existsByUserIdAndRoleId(target.getId(), superAdminRole.getId())) {
            return;
        }

        long activeSuperAdmins = userRoleRepository.countActiveUsersByRoleCode(SUPER_ADMIN);
        if (activeSuperAdmins <= 1) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of(
                    "operation", "Delete user",
                    "reason", "The last active SUPER_ADMIN account cannot be deleted"
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

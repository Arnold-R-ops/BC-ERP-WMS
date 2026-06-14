package com.wms.system.service;

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

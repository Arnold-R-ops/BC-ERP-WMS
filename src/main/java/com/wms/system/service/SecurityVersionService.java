package com.wms.system.service;

import com.wms.system.entity.User;
import com.wms.system.repository.SysRoleInheritRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Invalidates issued JWTs by advancing a user's authorization context version.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityVersionService {

    private final UserRepository userRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final SysRoleInheritRepository roleInheritRepository;

    /**
     * Advance the version of an already-loaded user entity.
     */
    public long bump(User user) {
        long current = user.getSecurityVersion() == null ? 0L : user.getSecurityVersion();
        long next = current + 1L;
        user.setSecurityVersion(next);
        return next;
    }

    /**
     * Advance one user's version inside the caller's transaction.
     */
    @Transactional
    public long bumpForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        long next = bump(user);
        userRepository.save(user);
        log.info("Security version advanced: userId={}, version={}", userId, next);
        return next;
    }

    /**
     * Advance all users whose effective permissions can be affected by a role.
     *
     * Users assigned a child role are included because child roles inherit
     * permissions from their parent roles.
     */
    @Transactional
    public void bumpForRoleAndDescendants(Long roleId) {
        Set<Long> affectedRoleIds = collectRoleAndDescendants(roleId);
        Set<Long> userIds = new HashSet<>();
        affectedRoleIds.forEach(id -> userIds.addAll(userRoleRepository.findUserIdsByRoleId(id)));
        bumpUsers(userIds);
        log.info("Security versions advanced for role change: roleId={}, affectedRoles={}, users={}",
                roleId, affectedRoleIds.size(), userIds.size());
    }

    /**
     * Advance every non-deleted user after a global permission definition change.
     */
    @Transactional
    public void bumpAllUsers() {
        List<User> users = userRepository.findAll();
        users.forEach(this::bump);
        userRepository.saveAll(users);
        log.warn("Security versions advanced for all users: count={}", users.size());
    }

    private Set<Long> collectRoleAndDescendants(Long roleId) {
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(roleId);

        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            roleInheritRepository.findChildRoleIdsByParentRoleId(current).stream()
                    .filter(childRoleId -> !visited.contains(childRoleId))
                    .forEach(queue::addLast);
        }

        return visited;
    }

    private void bumpUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        List<User> users = userRepository.findAllById(userIds);
        users.forEach(this::bump);
        userRepository.saveAll(users);
    }
}

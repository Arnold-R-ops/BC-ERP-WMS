package com.wms.system.signup.service;

import com.wms.system.dto.LoginResponse;
import com.wms.system.dto.UserPermissionDTO;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.UserRepository;
import com.wms.system.security.JwtProperties;
import com.wms.system.security.JwtUtil;
import com.wms.system.service.DynamicPermissionService;
import com.wms.system.service.UserRoleService;
import com.wms.system.signup.model.SessionHandoffCode;
import com.wms.system.signup.repository.SessionHandoffCodeRepository;
import com.wms.system.signup.security.SignupTokenHasher;
import com.wms.system.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SessionHandoffService {
    private final SessionHandoffCodeRepository repository;
    private final SignupTokenHasher tokenHasher;
    private final UserRepository userRepository;
    private final UserRoleService userRoleService;
    private final DynamicPermissionService permissionService;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;

    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse consume(String rawCode) {
        String hash = tokenHasher.hash("session-handoff", rawCode);
        SessionHandoffCode handoff = repository.findByCodeHashForUpdate(hash)
            .orElseThrow(() -> new BusinessException(ErrorKeys.SESSION_HANDOFF_INVALID));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (handoff.getConsumedAt() != null || !handoff.getExpiresAt().isAfter(now)) {
            throw new BusinessException(ErrorKeys.SESSION_HANDOFF_INVALID);
        }
        Long hostTenant = TenantContextHolder.requireTenant().tenantId();
        if (!handoff.getTenantId().equals(hostTenant)) {
            throw new BusinessException(ErrorKeys.SESSION_HANDOFF_INVALID);
        }

        User user = userRepository.findByIdAndCompanyId(
                handoff.getTenantUserId(), hostTenant)
            .filter(User::isEnabled)
            .filter(candidate -> !Boolean.TRUE.equals(candidate.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(ErrorKeys.SESSION_HANDOFF_INVALID));
        List<SysRole> activeRoles = userRoleService.getUserRoles(hostTenant, user.getId())
            .stream()
            .filter(SysRole::isActive)
            .sorted(Comparator.comparing(SysRole::getSortOrder,
                    Comparator.nullsLast(Integer::compareTo))
                .thenComparing(SysRole::getId))
            .toList();
        if (activeRoles.isEmpty()) {
            throw new BusinessException(ErrorKeys.SESSION_HANDOFF_INVALID);
        }
        SysRole currentRole = activeRoles.stream()
            .filter(role -> role.getId().equals(user.getDefaultRoleId()))
            .findFirst()
            .orElse(activeRoles.get(0));
        List<String> availableRoles = activeRoles.stream()
            .map(SysRole::getRoleCode)
            .toList();
        UserPermissionDTO permissions = permissionService.getUserPermissionsForRole(
            hostTenant, user.getId(), currentRole.getRoleCode(), user.getSecurityVersion());
        Set<String> permissionCodes = permissions.getPermissionCodes();
        String token = jwtUtil.generateTenantToken(
            user.getId(), hostTenant, user.getUsername(), currentRole.getRoleCode(),
            availableRoles, user.getSecurityVersion());

        handoff.setConsumedAt(now);
        repository.saveAndFlush(handoff);
        return LoginResponse.builder()
            .token(token)
            .tokenType("Bearer")
            .username(user.getUsername())
            .currentRole(currentRole.getRoleCode())
            .availableRoles(availableRoles)
            .permissionCodes(permissionCodes == null ? List.of()
                : permissionCodes.stream().sorted().toList())
            .expiresIn(jwtProperties.getExpiration())
            .sessionEndsAt(jwtUtil.getTenantSessionEndsAt(token))
            .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
            .build();
    }
}

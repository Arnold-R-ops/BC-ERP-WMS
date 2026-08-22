package com.wms.system.security;

import com.wms.system.platform.model.PlatformRole;
import com.wms.system.platform.model.PlatformUserRole;
import com.wms.system.platform.repository.PlatformRoleRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.platform.repository.PlatformUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlatformUserDetailsService {

    private final PlatformUserRepository platformUserRepository;
    private final PlatformUserRoleRepository platformUserRoleRepository;
    private final PlatformRoleRepository platformRoleRepository;

    @Transactional(readOnly = true)
    public PlatformSecurityUser loadEnabledUser(Long platformUserId) {
        return platformUserRepository.findById(platformUserId)
            .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
            .map(PlatformSecurityUser::new)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid platform token"));
    }

    @Transactional(readOnly = true)
    public List<String> loadRoleCodes(Long platformUserId) {
        Set<String> roles = new LinkedHashSet<>();
        for (PlatformUserRole assignment
                : platformUserRoleRepository.findByPlatformUserId(platformUserId)) {
            platformRoleRepository.findById(assignment.getPlatformRoleId())
                .map(PlatformRole::getRoleCode)
                .filter(code -> code != null && !code.isBlank())
                .ifPresent(roles::add);
        }
        return List.copyOf(roles);
    }
}

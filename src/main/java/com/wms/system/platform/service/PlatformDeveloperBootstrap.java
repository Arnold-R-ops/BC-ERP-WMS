package com.wms.system.platform.service;

import com.wms.system.platform.config.PlatformBootstrapProperties;
import com.wms.system.platform.model.PlatformRole;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.model.PlatformUserRole;
import com.wms.system.platform.repository.PlatformRoleRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.platform.repository.PlatformUserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** Creates the first platform developer account from a short-lived local secret. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformDeveloperBootstrap implements CommandLineRunner {
    private static final String DEVELOPER_ROLE = "PLATFORM_SUPER_ADMIN";

    private final PlatformBootstrapProperties properties;
    private final PlatformUserRepository userRepository;
    private final PlatformRoleRepository roleRepository;
    private final PlatformUserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (!properties.isEnabled()) return;

        String email = required(properties.getEmail(), "WMS_PLATFORM_BOOTSTRAP_EMAIL");
        String password = required(properties.getPassword(), "WMS_PLATFORM_BOOTSTRAP_PASSWORD");
        String displayName = required(properties.getDisplayName(), "platform bootstrap display name");
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (userRepository.findByNormalizedEmail(normalizedEmail).isPresent()) {
            log.info("[PLATFORM_BOOTSTRAP] Platform developer account already exists; no change made");
            return;
        }
        if (userRepository.count() != 0) {
            throw new IllegalStateException("Platform bootstrap is allowed only while platform_users is empty");
        }

        PlatformRole role = roleRepository.findByRoleCode(DEVELOPER_ROLE)
            .orElseThrow(() -> new IllegalStateException("Required platform role is missing"));
        PlatformUser user = userRepository.save(PlatformUser.builder()
            .normalizedEmail(normalizedEmail).passwordHash(passwordEncoder.encode(password))
            .displayName(displayName.trim()).enabled(true).securityVersion(1L).build());
        userRoleRepository.save(PlatformUserRole.builder()
            .platformUserId(user.getId()).platformRoleId(role.getId()).build());
        log.info("[PLATFORM_BOOTSTRAP] Created first platform developer account for {}. Password is never logged.", normalizedEmail);
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required when platform bootstrap is enabled");
        return value;
    }
}

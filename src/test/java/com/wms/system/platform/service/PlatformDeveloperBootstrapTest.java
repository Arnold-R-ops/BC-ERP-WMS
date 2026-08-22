package com.wms.system.platform.service;

import com.wms.system.platform.config.PlatformBootstrapProperties;
import com.wms.system.platform.model.PlatformRole;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformRoleRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.platform.repository.PlatformUserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformDeveloperBootstrapTest {
    @Mock private PlatformUserRepository userRepository;
    @Mock private PlatformRoleRepository roleRepository;
    @Mock private PlatformUserRoleRepository userRoleRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @Test void createsOnlyTheFirstReadExportPlatformAdministrator() throws Exception {
        PlatformBootstrapProperties properties = enabledProperties();
        when(userRepository.findByNormalizedEmail("developer@bcwms.com")).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(0L);
        when(roleRepository.findByRoleCode("PLATFORM_SUPER_ADMIN"))
            .thenReturn(Optional.of(PlatformRole.builder().id(11L).roleCode("PLATFORM_SUPER_ADMIN").build()));
        when(passwordEncoder.encode("one-time-secret")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(PlatformUser.class))).thenAnswer(invocation -> {
            PlatformUser user = invocation.getArgument(0); user.setId(22L); return user;
        });
        new PlatformDeveloperBootstrap(properties, userRepository, roleRepository, userRoleRepository, passwordEncoder).run();
        ArgumentCaptor<PlatformUser> userCaptor = ArgumentCaptor.forClass(PlatformUser.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getNormalizedEmail()).isEqualTo("developer@bcwms.com");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        verify(userRoleRepository).save(argThat(assignment -> assignment.getPlatformUserId().equals(22L)
            && assignment.getPlatformRoleId().equals(11L)));
    }

    @Test void existingDeveloperAccountIsNotChangedOnRestart() throws Exception {
        PlatformBootstrapProperties properties = enabledProperties();
        when(userRepository.findByNormalizedEmail("developer@bcwms.com"))
            .thenReturn(Optional.of(PlatformUser.builder().id(22L).normalizedEmail("developer@bcwms.com").build()));
        new PlatformDeveloperBootstrap(properties, userRepository, roleRepository, userRoleRepository, passwordEncoder).run();
        verify(userRepository, never()).save(any());
        verifyNoInteractions(roleRepository, userRoleRepository, passwordEncoder);
    }

    private PlatformBootstrapProperties enabledProperties() {
        PlatformBootstrapProperties properties = new PlatformBootstrapProperties();
        properties.setEnabled(true); properties.setEmail(" Developer@BCWMS.com ");
        properties.setPassword("one-time-secret"); properties.setDisplayName("平台超级管理员");
        return properties;
    }
}

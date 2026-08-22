package com.wms.system.platform.service;

import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformAccessGrantRepository;
import com.wms.system.security.PlatformSecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformAccessGuardTest {
    private final PlatformAccessGrantRepository grants = mock(PlatformAccessGrantRepository.class);
    private final PlatformAccessGuard guard = new PlatformAccessGuard(grants);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void readAndExportRolesRemainSeparate() {
        authenticate("ROLE_PLATFORM_TENANT_READ");
        when(grants.hasActive(eq(7L), eq("READ"), eq(1L), eq("users"), any())).thenReturn(true);
        assertThat(guard.requireRead(1L, "users").getId()).isEqualTo(7L);
        assertThatThrownBy(() -> guard.requireExport(1L, "users")).isInstanceOf(AccessDeniedException.class);

        authenticate("ROLE_PLATFORM_TENANT_EXPORT");
        when(grants.hasActive(eq(7L), eq("EXPORT"), eq(1L), eq("users"), any())).thenReturn(true);
        assertThat(guard.requireExport(1L, "users").getId()).isEqualTo(7L);
        assertThatThrownBy(() -> guard.requireRead(1L, "users")).isInstanceOf(AccessDeniedException.class);
    }

    @Test void superAdminCanReadAndExport() {
        authenticate("ROLE_PLATFORM_SUPER_ADMIN");
        assertThat(guard.requireRead(1L, "users").getId()).isEqualTo(7L);
        assertThat(guard.requireExport(1L, "users").getId()).isEqualTo(7L);
        assertThat(guard.requireAuditRead().getId()).isEqualTo(7L);
        assertThatThrownBy(guard::requireWrite).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(guard::requireDelete).isInstanceOf(AccessDeniedException.class);
    }

    @Test void delegatedReadAndExportRolesCannotReadPlatformAudit() {
        authenticate("ROLE_PLATFORM_TENANT_READ");
        assertThatThrownBy(guard::requireAuditRead).isInstanceOf(AccessDeniedException.class);
        authenticate("ROLE_PLATFORM_TENANT_EXPORT");
        assertThatThrownBy(guard::requireAuditRead).isInstanceOf(AccessDeniedException.class);
    }

    @Test void tenantDirectoryRequiresGrantMatchingTheCandidateRole() {
        authenticate("ROLE_PLATFORM_TENANT_READ");
        when(grants.activeTenantIds(eq(7L), eq("READ"), any())).thenReturn(List.of());
        assertThatThrownBy(guard::requireTenantDirectory).isInstanceOf(AccessDeniedException.class);

        when(grants.activeTenantIds(eq(7L), eq("READ"), any())).thenReturn(List.of(6L));
        assertThat(guard.requireTenantDirectory().getId()).isEqualTo(7L);
        assertThat(guard.activeTenantIds(guard.requirePlatformUser())).containsExactly(6L);
        verify(grants, never()).activeTenantIds(eq(7L), eq("EXPORT"), any());
    }

    @Test void writeAndDeleteRequireTheirOwnExplicitRoles() {
        authenticate("ROLE_PLATFORM_TENANT_WRITE");
        assertThat(guard.requireWrite().getId()).isEqualTo(7L);
        assertThatThrownBy(guard::requireDelete).isInstanceOf(AccessDeniedException.class);

        authenticate("ROLE_PLATFORM_TENANT_DELETE");
        assertThat(guard.requireDelete().getId()).isEqualTo(7L);
        assertThatThrownBy(guard::requireWrite).isInstanceOf(AccessDeniedException.class);
    }

    private void authenticate(String authority) {
        PlatformUser entity = PlatformUser.builder().id(7L).normalizedEmail("platform@example.com")
            .passwordHash("hidden").displayName("Platform").enabled(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformSecurityUser(entity), null, List.of(new SimpleGrantedAuthority(authority))));
    }
}

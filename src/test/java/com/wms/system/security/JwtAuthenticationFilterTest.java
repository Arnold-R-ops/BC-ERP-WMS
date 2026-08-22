package com.wms.system.security;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.dto.UserPermissionDTO;
import com.wms.system.entity.User;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.service.DynamicPermissionService;
import com.wms.system.tenant.config.TenancyProperties;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.TenantStatus;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private DynamicPermissionService permissionService;
    @Mock private PlatformUserDetailsService platformUserDetailsService;
    @Mock private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private TenancyProperties tenancyProperties;
    private SecurityUser securityUser;

    @BeforeEach
    void setUp() {
        tenancyProperties = new TenancyProperties();
        filter = new JwtAuthenticationFilter(
            jwtUtil,
            userDetailsService,
            permissionService,
            platformUserDetailsService,
            tenancyProperties
        );
        User user = User.builder()
            .id(12L)
            .username("worker")
            .password("encoded")
            .enabled(true)
            .securityVersion(5L)
            .build();
        user.setCompanyId(10L);
        securityUser = new SecurityUser(user);
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void authenticatesOnlyWhenCompanyTokenHostAndUserMatch() throws Exception {
        TenantContextHolder.set(companyContext(10L, "alpha"));
        MockHttpServletRequest request = authorizedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        PermissionDTO permission = PermissionDTO.builder()
            .permissionCode("inventory:view")
            .build();
        TenantJwtClaims claims = companyClaims(10L);

        when(jwtUtil.parseTenantToken("token")).thenReturn(claims);
        when(userDetailsService.loadTenantUser(12L, 10L)).thenReturn(securityUser);
        when(permissionService.getUserPermissionsForRole(
            10L, 12L, "WAREHOUSE_STAFF", 5L))
            .thenReturn(UserPermissionDTO.builder()
                .permissions(List.of(permission))
                .permissionCodes(Set.of("inventory:view"))
                .build());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
            .extracting(authority -> authority.getAuthority())
            .containsExactlyInAnyOrder(
                "ROLE_WAREHOUSE_STAFF", "WAREHOUSE_STAFF", "inventory:view");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void alphaTokenOnBetaHostIsRejectedBeforeAnyUserOrPermissionQuery()
            throws Exception {
        TenantContextHolder.set(companyContext(20L, "beta"));
        MockHttpServletRequest request = authorizedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtUtil.parseTenantToken("token")).thenReturn(companyClaims(10L));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_TOKEN_INVALID");
        assertThat(response.getContentAsString())
            .doesNotContain("alpha", "beta", "10", "20");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService, permissionService,
            platformUserDetailsService);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void platformHostNeverAttemptsToParseOrLoadCompanyUser() throws Exception {
        TenantContextHolder.set(new TenantContext(
            null, null, "platform.bcwms.com", RequestSurface.PLATFORM, null));
        MockHttpServletRequest request = authorizedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtUtil.parsePlatformToken("token"))
            .thenThrow(new io.jsonwebtoken.JwtException("wrong token type"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(userDetailsService, permissionService);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void localPlatformApiUsesPlatformTokenWhenTenancyRuntimeIsLockedOff()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
            "GET", "/api/platform/companies");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        PlatformJwtClaims claims = new PlatformJwtClaims(
            7L, "admin@bcwms.com", List.of("PLATFORM_SUPER_ADMIN"), 1L);
        PlatformSecurityUser platformUser = new PlatformSecurityUser(
            PlatformUser.builder()
                .id(7L)
                .normalizedEmail("admin@bcwms.com")
                .passwordHash("encoded")
                .displayName("Platform administrator")
                .enabled(true)
                .securityVersion(1L)
                .build());

        when(jwtUtil.parsePlatformToken("token")).thenReturn(claims);
        when(platformUserDetailsService.loadEnabledUser(7L)).thenReturn(platformUser);
        when(platformUserDetailsService.loadRoleCodes(7L))
            .thenReturn(List.of("PLATFORM_SUPER_ADMIN"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
            .extracting(authority -> authority.getAuthority())
            .containsExactly("ROLE_PLATFORM_SUPER_ADMIN");
        verify(jwtUtil, never()).parseTenantToken("token");
        verifyNoInteractions(userDetailsService, permissionService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void staleSecurityVersionIsRejectedWithoutPermissionLookup()
            throws Exception {
        TenantContextHolder.set(companyContext(10L, "alpha"));
        MockHttpServletRequest request = authorizedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        TenantJwtClaims stale = new TenantJwtClaims(
            12L, 10L, "worker", "WAREHOUSE_STAFF",
            List.of("WAREHOUSE_STAFF"), 4L);
        when(jwtUtil.parseTenantToken("token")).thenReturn(stale);
        when(userDetailsService.loadTenantUser(12L, 10L)).thenReturn(securityUser);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(permissionService);
        verify(filterChain, never()).doFilter(request, response);
    }

    private TenantJwtClaims companyClaims(Long companyId) {
        return new TenantJwtClaims(
            12L, companyId, "worker", "WAREHOUSE_STAFF",
            List.of("WAREHOUSE_STAFF"), 5L);
    }

    private TenantContext companyContext(Long companyId, String slug) {
        return new TenantContext(
            companyId,
            slug,
            slug + ".bcwms.com",
            RequestSurface.TENANT,
            TenantStatus.ACTIVE
        );
    }

    private MockHttpServletRequest authorizedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest(
            "GET", "/api/inventory/summary");
        request.addHeader("Authorization", "Bearer token");
        return request;
    }
}

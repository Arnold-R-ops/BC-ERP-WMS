package com.wms.system.security;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.dto.UserPermissionDTO;
import com.wms.system.entity.User;
import com.wms.system.service.DynamicPermissionService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private DynamicPermissionService permissionService;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private SecurityUser securityUser;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService, permissionService);
        securityUser = new SecurityUser(User.builder()
                .id(12L)
                .username("worker")
                .password("encoded")
                .enabled(true)
                .securityVersion(5L)
                .build());
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesWithOnlyActiveRolePermissions() throws Exception {
        MockHttpServletRequest request = authorizedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        PermissionDTO permission = PermissionDTO.builder()
                .permissionCode("inventory:view")
                .build();

        when(jwtUtil.extractUsername("token")).thenReturn("worker");
        when(jwtUtil.extractCurrentRole("token")).thenReturn("WAREHOUSE_STAFF");
        when(userDetailsService.loadUserByUsername("worker")).thenReturn(securityUser);
        when(jwtUtil.isTokenValid("token", "worker", 5L)).thenReturn(true);
        when(permissionService.getUserPermissionsForRole(
                12L, "WAREHOUSE_STAFF", 5L))
                .thenReturn(UserPermissionDTO.builder()
                        .permissions(List.of(permission))
                        .permissionCodes(Set.of("inventory:view"))
                        .build());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder(
                        "ROLE_WAREHOUSE_STAFF",
                        "WAREHOUSE_STAFF",
                        "inventory:view"
                );
        verify(permissionService).getUserPermissionsForRole(
                12L, "WAREHOUSE_STAFF", 5L);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejectsTokenWhenSecurityVersionIsStale() throws Exception {
        MockHttpServletRequest request = authorizedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.extractUsername("token")).thenReturn("worker");
        when(jwtUtil.extractCurrentRole("token")).thenReturn("WAREHOUSE_STAFF");
        when(userDetailsService.loadUserByUsername("worker")).thenReturn(securityUser);
        when(jwtUtil.isTokenValid("token", "worker", 5L)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(permissionService, never()).getUserPermissionsForRole(
                12L, "WAREHOUSE_STAFF", 5L);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejectsTokenWhenCurrentRoleIsNoLongerAssigned() throws Exception {
        MockHttpServletRequest request = authorizedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.extractUsername("token")).thenReturn("worker");
        when(jwtUtil.extractCurrentRole("token")).thenReturn("WAREHOUSE_STAFF");
        when(userDetailsService.loadUserByUsername("worker")).thenReturn(securityUser);
        when(jwtUtil.isTokenValid("token", "worker", 5L)).thenReturn(true);
        when(permissionService.getUserPermissionsForRole(
                12L, "WAREHOUSE_STAFF", 5L))
                .thenThrow(new IllegalArgumentException("Current role is not assigned"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    private MockHttpServletRequest authorizedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/inventory/summary");
        request.addHeader("Authorization", "Bearer token");
        return request;
    }
}

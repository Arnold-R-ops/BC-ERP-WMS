package com.wms.system.security;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.dto.UserPermissionDTO;
import com.wms.system.entity.User;
import com.wms.system.service.DynamicPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicAuthorizationManagerTest {

    private final DynamicPermissionService permissionService =
            mock(DynamicPermissionService.class);
    private final DynamicAuthorizationManager authorizationManager =
            new DynamicAuthorizationManager(permissionService);

    @Test
    void authenticationHealthEndpointIsPublic() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/health");
        Supplier<Authentication> unauthenticated = () -> null;

        AuthorizationDecision decision = authorizationManager.check(
                unauthenticated,
                new RequestAuthorizationContext(request)
        );

        assertThat(decision.isGranted()).isTrue();
    }

    @Test
    void otherAuthenticationEndpointsAreNotAccidentallyPublic() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/switch-role");
        Supplier<Authentication> unauthenticated = () -> null;

        AuthorizationDecision decision = authorizationManager.check(
                unauthenticated,
                new RequestAuthorizationContext(request)
        );

        assertThat(decision.isGranted()).isFalse();
    }

    @Test
    void requestAuthorizationUsesOnlyCurrentRoleAndLiveSecurityVersion() {
        User user = User.builder()
                .id(42L)
                .username("warehouse-user")
                .password("encoded")
                .enabled(true)
                .securityVersion(7L)
                .build();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new SecurityUser(user),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_STAFF"))
        );

        PermissionDTO inventoryView = PermissionDTO.builder()
                .permissionCode("inventory:view")
                .permissionType("API")
                .resourcePath("/api/inventory/**")
                .httpMethod("GET")
                .status("ACTIVE")
                .build();
        UserPermissionDTO activeRolePermissions = UserPermissionDTO.builder()
                .userId(42L)
                .roleCodes(Set.of("WAREHOUSE_STAFF"))
                .apiPermissions(List.of(inventoryView))
                .permissionCodes(Set.of("inventory:view"))
                .build();
        when(permissionService.getUserPermissionsForRole(
                42L, "WAREHOUSE_STAFF", 7L))
                .thenReturn(activeRolePermissions);

        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/inventory/summary");
        AuthorizationDecision decision = authorizationManager.check(
                () -> authentication,
                new RequestAuthorizationContext(request)
        );

        assertThat(decision.isGranted()).isTrue();
        verify(permissionService).getUserPermissionsForRole(
                42L, "WAREHOUSE_STAFF", 7L);
        verify(permissionService, never()).getUserPermissions(42L);
    }

    @Test
    void superAdminBypassDoesNotApplyWhenSuperAdminIsNotCurrentRole() {
        User user = User.builder()
                .id(43L)
                .username("multi-role-user")
                .password("encoded")
                .enabled(true)
                .securityVersion(2L)
                .build();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new SecurityUser(user),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_STAFF"))
        );
        when(permissionService.getUserPermissionsForRole(
                43L, "WAREHOUSE_STAFF", 2L))
                .thenReturn(UserPermissionDTO.builder()
                        .userId(43L)
                        .roleCodes(Set.of("WAREHOUSE_STAFF"))
                        .apiPermissions(List.of())
                        .permissionCodes(Set.of())
                        .build());

        MockHttpServletRequest request = new MockHttpServletRequest(
                "DELETE", "/api/users/99");
        AuthorizationDecision decision = authorizationManager.check(
                () -> authentication,
                new RequestAuthorizationContext(request)
        );

        assertThat(decision.isGranted()).isFalse();
    }
}

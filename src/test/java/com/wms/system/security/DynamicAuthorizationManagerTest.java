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
    void invitationMfaConfirmationIsPublicBeforeJwtIssuance() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/platform/auth/invitations/activate/confirm");
        Supplier<Authentication> unauthenticated = () -> null;

        AuthorizationDecision decision = authorizationManager.check(
                unauthenticated,
                new RequestAuthorizationContext(request)
        );

        assertThat(decision.isGranted()).isTrue();
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
        user.setCompanyId(1L);
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
                1L, 42L, "WAREHOUSE_STAFF", 7L))
                .thenReturn(activeRolePermissions);

        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/inventory/summary");
        AuthorizationDecision decision = authorizationManager.check(
                () -> authentication,
                new RequestAuthorizationContext(request)
        );

        assertThat(decision.isGranted()).isTrue();
        verify(permissionService).getUserPermissionsForRole(
                1L, 42L, "WAREHOUSE_STAFF", 7L);
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
        user.setCompanyId(1L);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new SecurityUser(user),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_STAFF"))
        );
        when(permissionService.getUserPermissionsForRole(
                1L, 43L, "WAREHOUSE_STAFF", 2L))
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

    @Test
    void correctionExecuteWildcardReachesPolicyImpactAndApplyAtPathLayer() {
        User user = User.builder()
                .id(44L)
                .username("correction-maker")
                .password("encoded")
                .enabled(true)
                .securityVersion(3L)
                .build();
        user.setCompanyId(1L);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new SecurityUser(user),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_INVENTORY_CORRECTION_MAKER"))
        );

        PermissionDTO correctionExecute = PermissionDTO.builder()
                .permissionCode("inventory:correction:execute")
                .permissionType("API")
                .resourcePath("/api/emergency-stock-corrections/**")
                .httpMethod("*")
                .status("ACTIVE")
                .build();
        when(permissionService.getUserPermissionsForRole(
                1L, 44L, "INVENTORY_CORRECTION_MAKER", 3L))
                .thenReturn(UserPermissionDTO.builder()
                        .userId(44L)
                        .roleCodes(Set.of("INVENTORY_CORRECTION_MAKER"))
                        .apiPermissions(List.of(correctionExecute))
                        .permissionCodes(Set.of("inventory:correction:execute"))
                        .build());

        for (MockHttpServletRequest request : List.of(
                new MockHttpServletRequest("GET", "/api/emergency-stock-corrections/policy"),
                new MockHttpServletRequest("GET", "/api/emergency-stock-corrections/99/impact"),
                new MockHttpServletRequest("POST", "/api/emergency-stock-corrections/99/apply")
        )) {
            AuthorizationDecision decision = authorizationManager.check(
                    () -> authentication,
                    new RequestAuthorizationContext(request)
            );
            assertThat(decision.isGranted()).isTrue();
        }
    }

    @Test
    void oneCapabilityCanDeclareSeveralExactRoutePatterns() {
        User user = User.builder()
                .id(45L)
                .username("buyer")
                .password("encoded")
                .enabled(true)
                .securityVersion(4L)
                .build();
        user.setCompanyId(1L);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new SecurityUser(user),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER"))
        );

        PermissionDTO submit = PermissionDTO.builder()
                .permissionCode("purchase:submit")
                .permissionType("API")
                .resourcePath("/api/purchase-orders/*/submit;/api/purchase-orders/*/withdraw")
                .httpMethod("POST")
                .status("ACTIVE")
                .build();
        when(permissionService.getUserPermissionsForRole(
                1L, 45L, "BUYER", 4L))
                .thenReturn(UserPermissionDTO.builder()
                        .userId(45L)
                        .roleCodes(Set.of("BUYER"))
                        .apiPermissions(List.of(submit))
                        .permissionCodes(Set.of("purchase:submit"))
                        .build());

        for (String route : List.of(
                "/api/purchase-orders/10/submit",
                "/api/purchase-orders/10/withdraw"
        )) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", route);
            assertThat(authorizationManager.check(
                    () -> authentication,
                    new RequestAuthorizationContext(request)
            ).isGranted()).isTrue();
        }

        MockHttpServletRequest unrelated = new MockHttpServletRequest(
                "POST", "/api/purchase-orders/10/cancel");
        assertThat(authorizationManager.check(
                () -> authentication,
                new RequestAuthorizationContext(unrelated)
        ).isGranted()).isFalse();
    }
}

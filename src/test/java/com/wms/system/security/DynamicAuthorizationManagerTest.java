package com.wms.system.security;

import com.wms.system.service.DynamicPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DynamicAuthorizationManagerTest {

    private final DynamicAuthorizationManager authorizationManager =
            new DynamicAuthorizationManager(mock(DynamicPermissionService.class));

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
}

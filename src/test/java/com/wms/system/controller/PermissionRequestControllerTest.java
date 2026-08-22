package com.wms.system.controller;

import com.wms.system.dto.PermissionRequestCreateRequest;
import com.wms.system.dto.PermissionRequestDTO;
import com.wms.system.dto.PermissionRequestReviewRequest;
import com.wms.system.dto.PermissionRequestRevokeRequest;
import com.wms.system.entity.User;
import com.wms.system.security.SecurityUser;
import com.wms.system.service.PermissionRequestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionRequestControllerTest {
    @Mock private PermissionRequestService service;
    @InjectMocks private PermissionRequestController controller;

    @Test
    void createsRequestWithTheActivatedAdministratorIdentity() {
        PermissionRequestCreateRequest request = PermissionRequestCreateRequest.builder()
            .targetUserId(8L)
            .requestedRoleId(4L)
            .build();
        PermissionRequestDTO result = PermissionRequestDTO.builder().id(12L).build();
        Authentication authentication = authentication(7L, "requester.admin", "TENANT_ADMIN");
        when(service.create(request, 7L, "requester.admin", "TENANT_ADMIN")).thenReturn(result);

        var response = controller.create(request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(result);
    }

    @Test
    void passesActivatedRoleIntoReviewIsolation() {
        PermissionRequestReviewRequest request = PermissionRequestReviewRequest.builder()
            .approved(true)
            .comment("Approved schedule")
            .build();
        Authentication authentication = authentication(9L, "security.admin", "SECURITY_ADMIN");

        controller.review(12L, request, authentication);

        verify(service).review(12L, request, 9L, "security.admin", "SECURITY_ADMIN");
    }

    @Test
    void revokesThroughDedicatedAuditedCommand() {
        PermissionRequestRevokeRequest request = PermissionRequestRevokeRequest.builder()
            .comment("Assignment ended")
            .build();
        Authentication authentication = authentication(7L, "admin", "TENANT_ADMIN");

        controller.revoke(12L, request, authentication);

        verify(service).revoke(12L, request, 7L, "admin", "TENANT_ADMIN");
    }

    private Authentication authentication(Long id, String username, String roleCode) {
        User user = User.builder().id(id).username(username).password("encoded").enabled(true).build();
        return new UsernamePasswordAuthenticationToken(
            new SecurityUser(user),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + roleCode))
        );
    }
}


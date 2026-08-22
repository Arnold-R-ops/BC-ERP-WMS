package com.wms.system.controller;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.dto.RoleCopyPreviewResponse;
import com.wms.system.dto.RoleCopyRequest;
import com.wms.system.dto.RoleCopyResult;
import com.wms.system.dto.RoleDTO;
import com.wms.system.dto.RoleGovernanceAuditDTO;
import com.wms.system.dto.RoleGovernanceResult;
import com.wms.system.dto.RolePackageCreateRequest;
import com.wms.system.dto.RolePackageDraftRequest;
import com.wms.system.dto.RoleReviewRequest;
import com.wms.system.dto.RoleReviewSubmitRequest;
import com.wms.system.dto.RoleRuntimeStatusRequest;
import com.wms.system.service.RoleCopyService;
import com.wms.system.service.RoleGovernanceService;
import com.wms.system.service.RolePermissionService;
import com.wms.system.service.RoleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock
    private RoleService roleService;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private RoleCopyService roleCopyService;
    @Mock
    private RoleGovernanceService roleGovernanceService;
    @InjectMocks
    private RoleController controller;

    @Test
    void listsAllRolesByDefault() {
        RoleDTO role = RoleDTO.builder().id(1L).roleCode("TENANT_ADMIN").build();
        when(roleService.getAllRoles()).thenReturn(List.of(role));

        var response = controller.getRoles(false);

        assertThat(response.getBody()).containsExactly(role);
        verify(roleService).getAllRoles();
    }

    @Test
    void listsOnlyActiveRolesWhenRequested() {
        RoleDTO role = RoleDTO.builder().id(2L).roleCode("WAREHOUSE_ADMIN").build();
        when(roleService.getAllActiveRoles()).thenReturn(List.of(role));

        var response = controller.getRoles(true);

        assertThat(response.getBody()).containsExactly(role);
        verify(roleService).getAllActiveRoles();
    }

    @Test
    void returnsRolePermissionsAfterRoleExistenceCheck() {
        RoleDTO role = RoleDTO.builder().id(2L).roleCode("WAREHOUSE_ADMIN").build();
        PermissionDTO permission = PermissionDTO.builder().id(10L).permissionCode("inventory:view").build();
        when(roleService.getRoleById(2L)).thenReturn(role);
        when(rolePermissionService.getRolePermissions(2L)).thenReturn(List.of(permission));

        var response = controller.getRolePermissions(2L);

        assertThat(response.getBody()).containsExactly(permission);
        verify(roleService).getRoleById(2L);
    }

    @Test
    void returnsCopyPreview() {
        RoleCopyPreviewResponse preview = RoleCopyPreviewResponse.builder()
                .sourceRoleId(2L).permissionCount(8).copyAllowed(true).build();
        when(roleCopyService.preview(2L)).thenReturn(preview);

        var response = controller.previewCopy(2L);

        assertThat(response.getBody()).isSameAs(preview);
        verify(roleCopyService).preview(2L);
    }

    @Test
    void createsCopiedRoleWithAuthenticatedOperator() {
        RoleCopyRequest request = RoleCopyRequest.builder()
                .roleCode("COPY").roleName("Copy").description("Purpose")
                .snapshotFingerprint("a".repeat(64)).build();
        RoleCopyResult result = RoleCopyResult.builder().auditId(9L).build();
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("admin");
        when(roleCopyService.copy(2L, request, 0L, "admin")).thenReturn(result);

        var response = controller.copyRole(2L, request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(result);
        verify(roleCopyService).copy(2L, request, 0L, "admin");
    }

    @Test
    void createsBlankDraftWithAuthenticatedOperator() {
        RolePackageCreateRequest request = RolePackageCreateRequest.builder()
                .roleCode("CUSTOM_SALES")
                .roleName("Custom sales")
                .description("Sales order entry")
                .permissionIds(java.util.Set.of(11L))
                .build();
        RoleGovernanceResult result = RoleGovernanceResult.builder().action("CREATE_DRAFT").build();
        Authentication authentication = new TestingAuthenticationToken(
                "security.admin",
                null,
                "ROLE_SECURITY_ADMIN"
        );
        when(roleGovernanceService.createDraft(request, 0L, "security.admin")).thenReturn(result);

        var response = controller.createDraft(request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(result);
        verify(roleGovernanceService).createDraft(request, 0L, "security.admin");
    }

    @Test
    void routesLifecycleCommandsWithTheAuthenticatedOperator() {
        Authentication authentication = new TestingAuthenticationToken(
                "security.admin",
                null,
                "ROLE_SECURITY_ADMIN"
        );
        RolePackageDraftRequest draft = RolePackageDraftRequest.builder()
                .roleName("Warehouse v2").description("Purpose").permissionIds(java.util.Set.of(11L)).build();
        RoleReviewSubmitRequest submission = RoleReviewSubmitRequest.builder().reason("Review").build();
        RoleReviewRequest review = RoleReviewRequest.builder().approved(true).comment("Approved").build();
        RoleRuntimeStatusRequest runtime = RoleRuntimeStatusRequest.builder()
                .reason("Release").confirmationCode("WAREHOUSE_V2").build();
        RoleGovernanceResult result = RoleGovernanceResult.builder().action("UPDATE_DRAFT").build();
        when(roleGovernanceService.updateDraft(9L, draft, 0L, "security.admin")).thenReturn(result);
        when(roleGovernanceService.submitForReview(9L, submission, 0L, "security.admin")).thenReturn(result);
        when(roleGovernanceService.review(9L, review, 0L, "security.admin", "SECURITY_ADMIN")).thenReturn(result);
        when(roleGovernanceService.activate(9L, runtime, 0L, "security.admin")).thenReturn(result);
        when(roleGovernanceService.deactivate(9L, runtime, 0L, "security.admin")).thenReturn(result);

        assertThat(controller.updateDraft(9L, draft, authentication).getBody()).isSameAs(result);
        assertThat(controller.submitReview(9L, submission, authentication).getBody()).isSameAs(result);
        assertThat(controller.review(9L, review, authentication).getBody()).isSameAs(result);
        assertThat(controller.activate(9L, runtime, authentication).getBody()).isSameAs(result);
        assertThat(controller.deactivate(9L, runtime, authentication).getBody()).isSameAs(result);

        verify(roleGovernanceService).updateDraft(9L, draft, 0L, "security.admin");
        verify(roleGovernanceService).submitForReview(9L, submission, 0L, "security.admin");
        verify(roleGovernanceService).review(9L, review, 0L, "security.admin", "SECURITY_ADMIN");
        verify(roleGovernanceService).activate(9L, runtime, 0L, "security.admin");
        verify(roleGovernanceService).deactivate(9L, runtime, 0L, "security.admin");
    }

    @Test
    void returnsImmutableGovernanceHistory() {
        RoleGovernanceAuditDTO audit = RoleGovernanceAuditDTO.builder().id(81L).action("APPROVE").build();
        when(roleGovernanceService.history(9L)).thenReturn(List.of(audit));

        assertThat(controller.history(9L).getBody()).containsExactly(audit);
        verify(roleGovernanceService).history(9L);
    }
}

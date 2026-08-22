package com.wms.system.service;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.dto.PermissionRequestCreateRequest;
import com.wms.system.dto.PermissionRequestDTO;
import com.wms.system.dto.PermissionRequestReviewRequest;
import com.wms.system.dto.PermissionRequestRevokeRequest;
import com.wms.system.entity.SysPermission;
import com.wms.system.entity.SysPermissionRequest;
import com.wms.system.entity.SysPermissionRequestAudit;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysPermissionRequestAuditRepository;
import com.wms.system.repository.SysPermissionRequestRepository;
import com.wms.system.repository.SysPermissionRequestWarehouseRepository;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionRequestServiceTest {
    @Mock private SysPermissionRequestRepository requestRepository;
    @Mock private SysPermissionRequestWarehouseRepository requestWarehouseRepository;
    @Mock private SysPermissionRequestAuditRepository auditRepository;
    @Mock private UserRepository userRepository;
    @Mock private SysRoleRepository roleRepository;
    @Mock private SysUserRoleRepository userRoleRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private DynamicPermissionService dynamicPermissionService;
    @Mock private UserRoleService userRoleService;
    @Mock private UserWarehouseService userWarehouseService;
    @Mock private UserManagementService userManagementService;

    @InjectMocks private PermissionRequestService service;

    private User target;
    private SysRole requestedRole;
    private SysRole baseRole;
    private PermissionDTO normalPermission;

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.findByIdAndCompanyId(any(Long.class), eq(1L)))
            .thenAnswer(invocation -> userRepository.findById(invocation.getArgument(0)));
        lenient().when(userRepository.findByCompanyIdAndIdForUpdate(eq(1L), any(Long.class)))
            .thenAnswer(invocation -> userRepository.findByIdForUpdate(invocation.getArgument(1)));
        lenient().when(roleRepository.findByCompanyIdAndId(eq(1L), any(Long.class)))
            .thenAnswer(invocation -> roleRepository.findById(invocation.getArgument(1)));
        lenient().when(roleRepository.findByCompanyIdAndIdForUpdate(eq(1L), any(Long.class)))
            .thenAnswer(invocation -> roleRepository.findByIdForUpdate(invocation.getArgument(1)));
        lenient().when(userRoleRepository.existsByCompanyIdAndUserIdAndRoleId(
                eq(1L), any(Long.class), any(Long.class)))
            .thenAnswer(invocation -> userRoleRepository.existsByUserIdAndRoleId(
                invocation.getArgument(1), invocation.getArgument(2)));
        lenient().when(userRoleRepository.findRoleIdsByCompanyIdAndUserId(
                eq(1L), any(Long.class)))
            .thenAnswer(invocation -> userRoleRepository.findRoleIdsByUserId(invocation.getArgument(1)));
        lenient().when(roleRepository.findByCompanyIdAndIdIn(eq(1L), any()))
            .thenAnswer(invocation -> roleRepository.findByIdIn(invocation.getArgument(1)));
        lenient().when(requestRepository.findByCompanyIdAndIdForUpdate(eq(1L), any(Long.class)))
            .thenAnswer(invocation -> requestRepository.findByIdForUpdate(invocation.getArgument(1)));
        lenient().when(requestRepository.findByCompanyIdAndId(eq(1L), any(Long.class)))
            .thenAnswer(invocation -> requestRepository.findById(invocation.getArgument(1)));
        lenient().when(auditRepository.findByCompanyIdAndPermissionRequestIdOrderByCreatedAtDesc(
                eq(1L), any(Long.class)))
            .thenAnswer(invocation -> auditRepository
                .findByPermissionRequestIdOrderByCreatedAtDesc(invocation.getArgument(1)));
        lenient().when(requestRepository.existsByCompanyIdAndTargetUserIdAndRequestedRoleIdAndStatus(
                eq(1L), any(Long.class), any(Long.class), any(String.class)))
            .thenAnswer(invocation -> requestRepository
                .existsByTargetUserIdAndRequestedRoleIdAndStatus(
                    invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)));
        target = User.builder()
            .id(2L)
            .username("warehouse.user")
            .password("encoded")
            .enabled(true)
            .isDeleted(false)
            .defaultRoleId(3L)
            .securityVersion(5L)
            .build();
        requestedRole = SysRole.builder()
            .id(4L)
            .roleCode("WAREHOUSE_ADMIN")
            .roleName("Warehouse administrator")
            .roleType(SysRole.ROLE_TYPE_SYSTEM)
            .systemCategory(SysRole.SYSTEM_CATEGORY_BUSINESS_TEMPLATE)
            .status("ACTIVE")
            .reviewStatus(SysRole.REVIEW_STATUS_APPROVED)
            .build();
        baseRole = SysRole.builder()
            .id(3L)
            .roleCode("GENERAL_MANAGER")
            .roleName("General manager")
            .roleType(SysRole.ROLE_TYPE_SYSTEM)
            .status("ACTIVE")
            .reviewStatus(SysRole.REVIEW_STATUS_APPROVED)
            .build();
        normalPermission = PermissionDTO.builder()
            .id(11L)
            .permissionCode("inventory:view")
            .riskLevel(SysPermission.RISK_LEVEL_NORMAL)
            .customAssignable(true)
            .build();
        lenient().when(auditRepository.save(any())).thenAnswer(invocation -> {
            SysPermissionRequestAudit audit = invocation.getArgument(0);
            audit.setId(900L);
            return audit;
        });
    }

    @Test
    void createsAuditedPendingRequestForOrdinaryActivePackage() {
        SysPermissionRequest request = prepareCreate(normalPermission);

        assertThat(request.getId()).isEqualTo(100L);
        assertThat(request.getStatus()).isEqualTo(SysPermissionRequest.STATUS_PENDING_REVIEW);
        assertThat(request.getSnapshotFingerprint()).hasSize(64);
        verify(auditRepository).save(any(SysPermissionRequestAudit.class));
        verify(userRoleService, never()).assignRoleToUser(any(), any(), any());
    }

    @Test
    void rejectsAdministratorSelfGrant() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.create(
            createInput(), 2L, target.getUsername(), SysRole.TENANT_ADMIN_ROLE_CODE
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorKey())
            .isEqualTo(ErrorKeys.PERMISSION_REQUEST_SELF_GRANT_FORBIDDEN);
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void securityAdministratorCannotTargetProtectedIdentity() {
        SysRole protectedRole = SysRole.builder()
            .id(9L)
            .roleCode(SysRole.TENANT_ADMIN_ROLE_CODE)
            .roleName("Super administrator")
            .roleType(SysRole.ROLE_TYPE_SYSTEM)
            .systemCategory(SysRole.SYSTEM_CATEGORY_PRIVILEGED)
            .status("ACTIVE")
            .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRoleRepository.findRoleIdsByUserId(2L)).thenReturn(Set.of(9L));
        when(roleRepository.findByIdIn(Set.of(9L))).thenReturn(List.of(protectedRole));

        assertThatThrownBy(() -> service.create(
            createInput(), 7L, "security.admin", SysRole.SECURITY_ADMIN_ROLE_CODE
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorKey())
            .isEqualTo(ErrorKeys.PERMISSION_REQUEST_PROTECTED_TARGET);
    }

    @Test
    void approvalAddsRoleWithoutReplacingExistingRolesAndWritesAudit() {
        SysPermissionRequest request = prepareCreate(normalPermission);
        when(requestRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(request));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(roleRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(requestedRole));
        when(userRoleService.getUserRoles(2L)).thenReturn(List.of(baseRole));

        PermissionRequestDTO result = service.review(
            100L,
            PermissionRequestReviewRequest.builder().approved(true).comment("Approved schedule").build(),
            8L,
            "reviewer.admin",
            SysRole.TENANT_ADMIN_ROLE_CODE
        );

        assertThat(result.getStatus()).isEqualTo(SysPermissionRequest.STATUS_APPROVED);
        assertThat(target.getDefaultRoleId()).isEqualTo(3L);
        verify(userRoleService).assignRoleToUser(2L, 4L, 8L);
        verify(userRoleService, never()).assignRolesToUser(any(), any(), any());
        verify(userWarehouseService).replaceAssignments(eq(2L), anyList(), eq(List.of()), eq(8L));
        verify(auditRepository, org.mockito.Mockito.times(2)).save(any(SysPermissionRequestAudit.class));
    }

    @Test
    void highRiskApprovalRequiresDifferentSuperAdministrator() {
        PermissionDTO highPermission = PermissionDTO.builder()
            .id(12L)
            .permissionCode("inventory:adjust")
            .riskLevel(SysPermission.RISK_LEVEL_HIGH)
            .customAssignable(true)
            .build();
        SysPermissionRequest request = prepareCreate(highPermission);
        when(requestRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(request));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(roleRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(requestedRole));

        assertThatThrownBy(() -> service.review(
            100L,
            PermissionRequestReviewRequest.builder().approved(true).build(),
            7L,
            "requester.admin",
            SysRole.TENANT_ADMIN_ROLE_CODE
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorKey())
            .isEqualTo(ErrorKeys.PERMISSION_REQUEST_SECOND_REVIEWER_REQUIRED);
        verify(userRoleService, never()).assignRoleToUser(any(), any(), any());
    }

    @Test
    void securityAdministratorMayRejectHighRiskRequestButCannotApproveIt() {
        PermissionDTO highPermission = PermissionDTO.builder()
            .id(12L)
            .permissionCode("inventory:adjust")
            .riskLevel(SysPermission.RISK_LEVEL_HIGH)
            .customAssignable(true)
            .build();
        SysPermissionRequest request = prepareCreate(highPermission);
        when(requestRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(request));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(target));

        PermissionRequestDTO result = service.review(
            100L,
            PermissionRequestReviewRequest.builder().approved(false).comment("Risk not justified").build(),
            8L,
            "security.admin",
            SysRole.SECURITY_ADMIN_ROLE_CODE
        );

        assertThat(result.getStatus()).isEqualTo(SysPermissionRequest.STATUS_REJECTED);
        verify(userRoleService, never()).assignRoleToUser(any(), any(), any());
    }

    @Test
    void revocationRemovesOnlyRequestedRoleAndAdvancesThroughExistingSecurityBoundary() {
        SysPermissionRequest request = prepareCreate(normalPermission);
        request.setStatus(SysPermissionRequest.STATUS_APPROVED);
        when(requestRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(request));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(roleRepository.findById(4L)).thenReturn(Optional.of(requestedRole));
        when(userRoleRepository.existsByUserIdAndRoleId(2L, 4L)).thenReturn(true);
        when(userRoleService.getUserRoles(2L)).thenReturn(List.of(baseRole, requestedRole));

        PermissionRequestDTO result = service.revoke(
            100L,
            PermissionRequestRevokeRequest.builder().comment("Assignment ended").build(),
            8L,
            "security.admin",
            SysRole.SECURITY_ADMIN_ROLE_CODE
        );

        assertThat(result.getStatus()).isEqualTo(SysPermissionRequest.STATUS_REVOKED);
        verify(userManagementService).validateRoleReplacement(2L, 8L, Set.of(3L), SysRole.SECURITY_ADMIN_ROLE_CODE);
        verify(userRoleService).removeRoleFromUser(2L, 4L);
        verify(userRoleService, never()).assignRolesToUser(any(), any(), any());
    }

    @Test
    void auditHistoryRemainsReadableAfterTargetAccountIsDeleted() {
        SysPermissionRequest request = SysPermissionRequest.builder()
            .id(100L)
            .targetUserId(2L)
            .targetUsername("deleted.user")
            .requestedRoleId(4L)
            .requestedRoleCode("SALESPERSON")
            .requestedRoleName("Salesperson")
            .snapshotFingerprint("a".repeat(64))
            .submittedBy(7L)
            .submittedByUsername("requester.admin")
            .build();
        SysPermissionRequestAudit audit = SysPermissionRequestAudit.builder()
            .id(901L)
            .permissionRequestId(100L)
            .action("CREATE")
            .operatorId(7L)
            .operatorUsername("requester.admin")
            .operatorRoleCode(SysRole.TENANT_ADMIN_ROLE_CODE)
            .targetUserId(2L)
            .targetUsername("deleted.user")
            .requestedRoleId(4L)
            .requestedRoleCode("SALESPERSON")
            .toStatus(SysPermissionRequest.STATUS_PENDING_REVIEW)
            .highRiskPermissionCount(0)
            .build();
        when(requestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(auditRepository.findByPermissionRequestIdOrderByCreatedAtDesc(100L)).thenReturn(List.of(audit));

        assertThat(service.history(100L, SysRole.TENANT_ADMIN_ROLE_CODE))
            .singleElement()
            .extracting("action")
            .isEqualTo("CREATE");
        verify(userRepository, never()).findById(2L);
    }

    private SysPermissionRequest prepareCreate(PermissionDTO permission) {
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(roleRepository.findById(4L)).thenReturn(Optional.of(requestedRole));
        when(userRoleRepository.existsByUserIdAndRoleId(2L, 4L)).thenReturn(false);
        when(requestRepository.existsByTargetUserIdAndRequestedRoleIdAndStatus(
            2L, 4L, SysPermissionRequest.STATUS_PENDING_REVIEW
        )).thenReturn(false);
        when(dynamicPermissionService.getInheritedRoleIds(Set.of(4L))).thenReturn(Set.of(4L));
        when(dynamicPermissionService.getPermissionsByRoleIds(Set.of(4L))).thenReturn(List.of(permission));
        final SysPermissionRequest[] savedRequest = new SysPermissionRequest[1];
        when(requestRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            savedRequest[0] = invocation.getArgument(0);
            savedRequest[0].setId(100L);
            return savedRequest[0];
        });
        service.create(createInput(), 7L, "requester.admin", SysRole.TENANT_ADMIN_ROLE_CODE);
        return savedRequest[0];
    }

    private PermissionRequestCreateRequest createInput() {
        return PermissionRequestCreateRequest.builder()
            .targetUserId(2L)
            .requestedRoleId(4L)
            .requestReason("Night shift access")
            .build();
    }
}

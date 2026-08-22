package com.wms.system.service;

import com.wms.system.dto.RoleGovernanceResult;
import com.wms.system.dto.RolePackageCreateRequest;
import com.wms.system.dto.RolePackageDraftRequest;
import com.wms.system.dto.RoleReviewRequest;
import com.wms.system.dto.RoleReviewSubmitRequest;
import com.wms.system.dto.RoleRuntimeStatusRequest;
import com.wms.system.entity.SysApprovalTemplate;
import com.wms.system.entity.SysPermission;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysRoleGovernanceAudit;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysApprovalTemplateRepository;
import com.wms.system.repository.SysPermissionRepository;
import com.wms.system.repository.SysRoleGovernanceAuditRepository;
import com.wms.system.repository.SysRoleInheritRepository;
import com.wms.system.repository.SysRolePermissionRepository;
import com.wms.system.repository.SysRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleGovernanceServiceTest {

    @Mock private SysRoleRepository roleRepository;
    @Mock private SysPermissionRepository permissionRepository;
    @Mock private SysRolePermissionRepository rolePermissionRepository;
    @Mock private SysRoleInheritRepository roleInheritRepository;
    @Mock private SysApprovalTemplateRepository approvalTemplateRepository;
    @Mock private SysRoleGovernanceAuditRepository auditRepository;
    @Mock private RoleService roleService;
    @Mock private PermissionCacheService cacheService;
    @Mock private SecurityVersionService securityVersionService;

    @InjectMocks private RoleGovernanceService service;

    private SysRole role;
    private SysPermission normalPermission;
    private SysPermission highPermission;

    @BeforeEach
    void setUp() {
        lenient().when(roleRepository.existsByCompanyIdAndRoleCode(eq(1L), any(String.class)))
            .thenAnswer(invocation -> roleRepository.existsByRoleCode(invocation.getArgument(1)));
        lenient().when(roleRepository.findByCompanyIdAndIdForUpdate(eq(1L), any(Long.class)))
            .thenAnswer(invocation -> roleRepository.findByIdForUpdate(invocation.getArgument(1)));
        lenient().when(roleInheritRepository.findParentRoleIdsByCompanyIdAndChildRoleId(
                eq(1L), any(Long.class)))
            .thenAnswer(invocation -> roleInheritRepository
                .findParentRoleIdsByChildRoleId(invocation.getArgument(1)));
        lenient().when(rolePermissionRepository.findPermissionIdsByCompanyIdAndRoleId(
                eq(1L), any(Long.class)))
            .thenAnswer(invocation -> rolePermissionRepository
                .findPermissionIdsByRoleId(invocation.getArgument(1)));
        lenient().when(permissionRepository.findByCompanyIdAndIdIn(eq(1L), any()))
            .thenAnswer(invocation -> permissionRepository.findByIdIn(invocation.getArgument(1)));
        role = SysRole.builder()
                .id(41L)
                .roleCode("CUSTOM_WAREHOUSE")
                .roleName("Custom warehouse")
                .description("Warehouse operating package")
                .roleType(SysRole.ROLE_TYPE_CUSTOM)
                .importAllowed(true)
                .approvalTemplateCode(SysRole.SIMPLE_APPROVAL_TEMPLATE_CODE)
                .reviewStatus(SysRole.REVIEW_STATUS_DRAFT)
                .status("DISABLED")
                .sortOrder(100)
                .build();
        role.setCompanyId(1L);

        normalPermission = permission(11L, "inventory:view", SysPermission.RISK_LEVEL_NORMAL);
        highPermission = permission(12L, "outbound:pick", SysPermission.RISK_LEVEL_HIGH);

        lenient().when(auditRepository.save(any())).thenAnswer(invocation -> {
            SysRoleGovernanceAudit audit = invocation.getArgument(0);
            audit.setId(901L);
            return audit;
        });
    }

    @Test
    void createsAnIndependentDisabledDraftFromCheckedPermissions() {
        when(roleRepository.existsByRoleCode("CUSTOM_SALES")).thenReturn(false);
        when(roleRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SysRole saved = invocation.getArgument(0);
            saved.setId(51L);
            return saved;
        });
        when(permissionRepository.findByIdIn(Set.of(11L, 12L)))
                .thenReturn(List.of(normalPermission, highPermission));

        RoleGovernanceResult result = service.createDraft(
                RolePackageCreateRequest.builder()
                        .roleCode("custom_sales")
                        .roleName("Custom sales")
                        .description("Sales order entry")
                        .permissionIds(Set.of(11L, 12L))
                        .build(),
                7L,
                "security.admin"
        );

        assertThat(result.getAction()).isEqualTo("CREATE_DRAFT");
        assertThat(result.getPermissionCount()).isEqualTo(2);
        assertThat(result.getHighRiskCount()).isEqualTo(1);
        ArgumentCaptor<SysRole> roleCaptor = ArgumentCaptor.forClass(SysRole.class);
        verify(roleRepository).saveAndFlush(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getRoleCode()).isEqualTo("CUSTOM_SALES");
        assertThat(roleCaptor.getValue().getRoleType()).isEqualTo(SysRole.ROLE_TYPE_CUSTOM);
        assertThat(roleCaptor.getValue().getReviewStatus()).isEqualTo(SysRole.REVIEW_STATUS_DRAFT);
        assertThat(roleCaptor.getValue().getStatus()).isEqualTo("DISABLED");
        verify(rolePermissionRepository).saveAll(any());

        ArgumentCaptor<SysRoleGovernanceAudit> auditCaptor = ArgumentCaptor.forClass(SysRoleGovernanceAudit.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("CREATE_DRAFT");
        assertThat(auditCaptor.getValue().getAddedPermissionCodes())
                .isEqualTo("inventory:view,outbound:pick");
    }

    @Test
    void blankDraftCreationRejectsProtectedPermissions() {
        SysPermission protectedPermission = permission(
                13L,
                "system:admin",
                SysPermission.RISK_LEVEL_CRITICAL
        );
        protectedPermission.setCustomAssignable(false);
        when(roleRepository.existsByRoleCode("CUSTOM_ADMIN")).thenReturn(false);
        when(roleRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SysRole saved = invocation.getArgument(0);
            saved.setId(52L);
            return saved;
        });
        when(permissionRepository.findByIdIn(Set.of(13L))).thenReturn(List.of(protectedPermission));

        assertThatThrownBy(() -> service.createDraft(
                RolePackageCreateRequest.builder()
                        .roleCode("CUSTOM_ADMIN")
                        .roleName("Forbidden custom admin")
                        .description("Must be rejected")
                        .permissionIds(Set.of(13L))
                        .build(),
                7L,
                "security.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorKey())
                .isEqualTo(ErrorKeys.ROLE_PACKAGE_PERMISSION_NOT_ASSIGNABLE);
        verify(rolePermissionRepository, never()).saveAll(any());
        verify(auditRepository, never()).save(any());
    }

    @Test
    void updatesOnlyDisabledCustomDraftsAndReplacesTheDirectSnapshot() {
        when(roleRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(role));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(41L)).thenReturn(Set.of());
        when(rolePermissionRepository.findPermissionIdsByRoleId(41L)).thenReturn(Set.of(11L));
        when(permissionRepository.findByIdIn(Set.of(11L, 12L)))
                .thenReturn(List.of(normalPermission, highPermission));
        when(permissionRepository.findByIdIn(Set.of(12L))).thenReturn(List.of(highPermission));

        RoleGovernanceResult result = service.updateDraft(
                41L,
                RolePackageDraftRequest.builder()
                        .roleName("Warehouse custom v2")
                        .description("Updated purpose")
                        .permissionIds(Set.of(11L, 12L))
                        .reason("Add controlled picking")
                        .build(),
                7L,
                "security.admin"
        );

        assertThat(result.getAction()).isEqualTo("UPDATE_DRAFT");
        assertThat(result.getHighRiskCount()).isEqualTo(1);
        assertThat(role.getRoleName()).isEqualTo("Warehouse custom v2");
        verify(rolePermissionRepository).deleteByCompanyIdAndRoleId(1L, 41L);
        verify(rolePermissionRepository).saveAll(any());
        verify(securityVersionService).bumpForRoleAndDescendants(41L);

        ArgumentCaptor<SysRoleGovernanceAudit> auditCaptor = ArgumentCaptor.forClass(SysRoleGovernanceAudit.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAddedPermissionCodes()).isEqualTo("outbound:pick");
    }

    @Test
    void highRiskSubmissionRequiresAReason() {
        when(roleRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(role));
        when(approvalTemplateRepository.findByCompanyIdAndTemplateCode(1L, SysRole.SIMPLE_APPROVAL_TEMPLATE_CODE))
                .thenReturn(Optional.of(simpleTemplate()));
        when(rolePermissionRepository.findPermissionIdsByRoleId(41L)).thenReturn(Set.of(12L));
        when(permissionRepository.findByIdIn(Set.of(12L))).thenReturn(List.of(highPermission));

        assertThatThrownBy(() -> service.submitForReview(
                41L,
                new RoleReviewSubmitRequest(),
                7L,
                "security.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorKey())
                .isEqualTo(ErrorKeys.ROLE_PACKAGE_HIGH_RISK_REASON_REQUIRED);
    }

    @Test
    void highRiskSimpleApprovalRequiresAnotherAccount() {
        role.setReviewStatus(SysRole.REVIEW_STATUS_PENDING);
        role.setReviewSubmittedBy(7L);
        role.setReviewSubmittedByUsername("security.admin");
        when(roleRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(role));
        when(approvalTemplateRepository.findByCompanyIdAndTemplateCode(1L, SysRole.SIMPLE_APPROVAL_TEMPLATE_CODE))
                .thenReturn(Optional.of(simpleTemplate()));
        when(rolePermissionRepository.findPermissionIdsByRoleId(41L)).thenReturn(Set.of(12L));
        when(permissionRepository.findByIdIn(Set.of(12L))).thenReturn(List.of(highPermission));

        assertThatThrownBy(() -> service.review(
                41L,
                RoleReviewRequest.builder().approved(true).comment("Looks good").build(),
                7L,
                "security.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorKey())
                .isEqualTo(ErrorKeys.ROLE_PACKAGE_SECOND_REVIEWER_REQUIRED);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void highRiskApprovalRejectsSecurityAdministratorEvenWhenReviewerIsDifferent() {
        role.setReviewStatus(SysRole.REVIEW_STATUS_PENDING);
        role.setReviewSubmittedBy(7L);
        role.setReviewSubmittedByUsername("package.submitter");
        when(roleRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(role));
        when(approvalTemplateRepository.findByCompanyIdAndTemplateCode(1L, SysRole.SIMPLE_APPROVAL_TEMPLATE_CODE))
                .thenReturn(Optional.of(simpleTemplate()));
        when(rolePermissionRepository.findPermissionIdsByRoleId(41L)).thenReturn(Set.of(12L));
        when(permissionRepository.findByIdIn(Set.of(12L))).thenReturn(List.of(highPermission));

        assertThatThrownBy(() -> service.review(
                41L,
                RoleReviewRequest.builder().approved(true).comment("Approved").build(),
                8L,
                "security.reviewer",
                SysRole.SECURITY_ADMIN_ROLE_CODE
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorKey())
                .isEqualTo(ErrorKeys.ROLE_PACKAGE_SECOND_REVIEWER_REQUIRED);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void highRiskApprovalAllowsAnotherSuperAdministrator() {
        role.setReviewStatus(SysRole.REVIEW_STATUS_PENDING);
        role.setReviewSubmittedBy(7L);
        role.setReviewSubmittedByUsername("package.submitter");
        when(roleRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(role));
        when(approvalTemplateRepository.findByCompanyIdAndTemplateCode(1L, SysRole.SIMPLE_APPROVAL_TEMPLATE_CODE))
                .thenReturn(Optional.of(simpleTemplate()));
        when(rolePermissionRepository.findPermissionIdsByRoleId(41L)).thenReturn(Set.of(12L));
        when(permissionRepository.findByIdIn(Set.of(12L))).thenReturn(List.of(highPermission));

        RoleGovernanceResult result = service.review(
                41L,
                RoleReviewRequest.builder().approved(true).comment("Approved").build(),
                8L,
                "super.reviewer",
                SysRole.TENANT_ADMIN_ROLE_CODE
        );

        assertThat(result.getAction()).isEqualTo("APPROVE");
        assertThat(role.getReviewStatus()).isEqualTo(SysRole.REVIEW_STATUS_APPROVED);
    }

    @Test
    void normalRiskSimpleApprovalAllowsTheSubmitterToReview() {
        role.setReviewStatus(SysRole.REVIEW_STATUS_PENDING);
        role.setReviewSubmittedBy(7L);
        role.setReviewSubmittedByUsername("security.admin");
        when(roleRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(role));
        when(approvalTemplateRepository.findByCompanyIdAndTemplateCode(1L, SysRole.SIMPLE_APPROVAL_TEMPLATE_CODE))
                .thenReturn(Optional.of(simpleTemplate()));
        when(rolePermissionRepository.findPermissionIdsByRoleId(41L)).thenReturn(Set.of(11L));
        when(permissionRepository.findByIdIn(Set.of(11L))).thenReturn(List.of(normalPermission));

        RoleGovernanceResult result = service.review(
                41L,
                RoleReviewRequest.builder().approved(true).comment("Approved").build(),
                7L,
                "security.admin"
        );

        assertThat(result.getAction()).isEqualTo("APPROVE");
        assertThat(role.getReviewStatus()).isEqualTo(SysRole.REVIEW_STATUS_APPROVED);
        assertThat(role.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void rejectionReturnsThePackageToDraftAndRequiresAComment() {
        role.setReviewStatus(SysRole.REVIEW_STATUS_PENDING);
        when(roleRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(role));
        when(approvalTemplateRepository.findByCompanyIdAndTemplateCode(1L, SysRole.SIMPLE_APPROVAL_TEMPLATE_CODE))
                .thenReturn(Optional.of(simpleTemplate()));
        when(rolePermissionRepository.findPermissionIdsByRoleId(41L)).thenReturn(Set.of(11L));
        when(permissionRepository.findByIdIn(Set.of(11L))).thenReturn(List.of(normalPermission));

        assertThatThrownBy(() -> service.review(
                41L,
                RoleReviewRequest.builder().approved(false).build(),
                8L,
                "reviewer"
        )).isInstanceOf(BusinessException.class);

        RoleGovernanceResult result = service.review(
                41L,
                RoleReviewRequest.builder().approved(false).comment("Remove excess access").build(),
                8L,
                "reviewer"
        );
        assertThat(result.getAction()).isEqualTo("REJECT");
        assertThat(role.getReviewStatus()).isEqualTo(SysRole.REVIEW_STATUS_DRAFT);
    }

    @Test
    void activationRequiresApprovedStateAndTypedCode() {
        role.setReviewStatus(SysRole.REVIEW_STATUS_APPROVED);
        when(roleRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.activate(
                41L,
                RoleRuntimeStatusRequest.builder()
                        .reason("Release approved package")
                        .confirmationCode("WRONG")
                        .build(),
                8L,
                "reviewer"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorKey())
                .isEqualTo(ErrorKeys.ROLE_PACKAGE_CONFIRMATION_REQUIRED);
    }

    @Test
    void deactivationRemainsAvailableWhenAHistoricalPermissionIsNoLongerAssignable() {
        role.setReviewStatus(SysRole.REVIEW_STATUS_APPROVED);
        role.setStatus("ACTIVE");
        SysPermission retiredPermission = permission(13L, "inventory:retired", SysPermission.RISK_LEVEL_CRITICAL);
        retiredPermission.setStatus("DISABLED");
        retiredPermission.setCustomAssignable(false);
        when(roleRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.findPermissionIdsByRoleId(41L)).thenReturn(Set.of(13L));
        when(permissionRepository.findByIdIn(Set.of(13L))).thenReturn(List.of(retiredPermission));

        RoleGovernanceResult result = service.deactivate(
                41L,
                RoleRuntimeStatusRequest.builder()
                        .reason("Emergency access revocation")
                        .confirmationCode("CUSTOM_WAREHOUSE")
                        .build(),
                8L,
                "reviewer"
        );

        assertThat(result.getAction()).isEqualTo("DEACTIVATE");
        assertThat(role.getStatus()).isEqualTo("DISABLED");
        verify(roleRepository).save(role);
        verify(securityVersionService).bumpForRoleAndDescendants(41L);
    }

    @Test
    void protectedSystemTemplatesCannotEnterTheOnlineWorkflow() {
        role.setRoleType(SysRole.ROLE_TYPE_SYSTEM);
        when(roleRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.updateDraft(
                41L,
                RolePackageDraftRequest.builder()
                        .roleName("Changed")
                        .description("Changed")
                        .permissionIds(Set.of())
                        .build(),
                7L,
                "security.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorKey())
                .isEqualTo(ErrorKeys.ROLE_PACKAGE_NOT_CUSTOM);
    }

    private SysPermission permission(Long id, String code, String riskLevel) {
        return SysPermission.builder()
                .id(id)
                .permissionCode(code)
                .permissionName(code)
                .permissionType("API")
                .riskLevel(riskLevel)
                .customAssignable(true)
                .status("ACTIVE")
                .sortOrder(1)
                .build();
    }

    private SysApprovalTemplate simpleTemplate() {
        SysApprovalTemplate template = SysApprovalTemplate.builder()
                .id(1L)
                .templateCode(SysRole.SIMPLE_APPROVAL_TEMPLATE_CODE)
                .templateName("Simple")
                .objectType("ROLE_PACKAGE")
                .approvalMode("SIMPLE")
                .status("ACTIVE")
                .systemDefined(true)
                .versionNo(1)
                .build();
        template.setCompanyId(1L);
        return template;
    }
}

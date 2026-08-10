package com.wms.system.service;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.dto.RoleCopyRequest;
import com.wms.system.dto.RoleDTO;
import com.wms.system.entity.SysPermission;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysRoleCopyAudit;
import com.wms.system.entity.SysRolePermission;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysRoleCopyAuditRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleCopyServiceTest {

    @Mock private SysRoleRepository roleRepository;
    @Mock private SysRolePermissionRepository rolePermissionRepository;
    @Mock private SysRoleCopyAuditRepository auditRepository;
    @Mock private DynamicPermissionService dynamicPermissionService;
    @Mock private RoleService roleService;

    @InjectMocks private RoleCopyService service;

    private SysRole source;
    private PermissionDTO normalPermission;
    private PermissionDTO highPermission;

    @BeforeEach
    void setUp() {
        source = SysRole.builder()
                .id(7L)
                .roleCode("WAREHOUSE_STAFF")
                .roleName("Warehouse Staff")
                .roleType(SysRole.ROLE_TYPE_SYSTEM)
                .systemCategory(SysRole.SYSTEM_CATEGORY_BUSINESS_TEMPLATE)
                .importAllowed(true)
                .status("ACTIVE")
                .sortOrder(40)
                .build();
        source.setCompanyId(1L);

        normalPermission = permission(10L, "inbound:view", SysPermission.RISK_LEVEL_NORMAL, true);
        highPermission = permission(11L, "outbound:pick", SysPermission.RISK_LEVEL_HIGH, true);
    }

    @Test
    void previewReturnsEffectiveSnapshotAndRiskCounts() {
        arrangeSnapshot(List.of(highPermission, normalPermission));

        var preview = service.preview(7L);

        assertThat(preview.getPermissionCount()).isEqualTo(2);
        assertThat(preview.getNormalRiskCount()).isEqualTo(1);
        assertThat(preview.getHighRiskCount()).isEqualTo(1);
        assertThat(preview.getCriticalRiskCount()).isZero();
        assertThat(preview.getCopyAllowed()).isTrue();
        assertThat(preview.getPermissions())
                .extracting(PermissionDTO::getPermissionCode)
                .containsExactly("inbound:view", "outbound:pick");
        assertThat(preview.getSnapshotFingerprint()).hasSize(64);
    }

    @Test
    void previewRejectsProtectedSource() {
        source.setRoleCode(SysRole.SUPER_ADMIN_ROLE_CODE);
        source.setSystemCategory(SysRole.SYSTEM_CATEGORY_PRIVILEGED);
        source.setImportAllowed(false);
        when(roleRepository.findById(7L)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.preview(7L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorKey()).isEqualTo(ErrorKeys.ROLE_COPY_SOURCE_NOT_ALLOWED));
    }

    @Test
    void previewRejectsDisabledSource() {
        source.setStatus("DISABLED");
        when(roleRepository.findById(7L)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.preview(7L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorKey()).isEqualTo(ErrorKeys.ROLE_COPY_SOURCE_DISABLED));
    }

    @Test
    void criticalPermissionBlocksCopy() {
        PermissionDTO critical = permission(
                12L, "system:admin", SysPermission.RISK_LEVEL_CRITICAL, false);
        arrangeSnapshot(List.of(critical));
        var preview = service.preview(7L);

        assertThat(preview.getCopyAllowed()).isFalse();
        assertThat(preview.getCriticalRiskCount()).isEqualTo(1);

        RoleCopyRequest request = validRequest(preview.getSnapshotFingerprint());
        assertThatThrownBy(() -> service.copy(7L, request, 1L, "admin"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorKey()).isEqualTo(ErrorKeys.ROLE_COPY_CRITICAL_PERMISSION));
        verify(roleRepository, never()).saveAndFlush(any());
    }

    @Test
    void highRiskCopyRequiresAcknowledgementReasonAndTypedCode() {
        arrangeSnapshot(List.of(highPermission));
        var preview = service.preview(7L);
        RoleCopyRequest request = validRequest(preview.getSnapshotFingerprint());

        assertThatThrownBy(() -> service.copy(7L, request, 1L, "admin"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorKey())
                                .isEqualTo(ErrorKeys.ROLE_COPY_RISK_CONFIRMATION_REQUIRED));
        verify(roleRepository, never()).saveAndFlush(any());
    }

    @Test
    void changedSnapshotIsRejectedBeforeCreation() {
        arrangeSnapshot(List.of(normalPermission));

        RoleCopyRequest request = validRequest("0".repeat(64));
        assertThatThrownBy(() -> service.copy(7L, request, 1L, "admin"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorKey()).isEqualTo(ErrorKeys.ROLE_COPY_SNAPSHOT_STALE));
        verify(roleRepository, never()).saveAndFlush(any());
    }

    @Test
    void copyCreatesDisabledCustomRoleWithDirectSnapshotAndAudit() {
        arrangeSnapshot(List.of(normalPermission, highPermission));
        var preview = service.preview(7L);
        RoleCopyRequest request = validRequest(preview.getSnapshotFingerprint());
        request.setRiskAcknowledged(true);
        request.setConfirmationCode("warehouse_copy");
        request.setOperationReason("Temporary warehouse team baseline");

        when(roleRepository.existsByRoleCode("WAREHOUSE_COPY")).thenReturn(false);
        when(roleRepository.saveAndFlush(any(SysRole.class))).thenAnswer(invocation -> {
            SysRole role = invocation.getArgument(0);
            role.setId(99L);
            return role;
        });
        when(auditRepository.save(any(SysRoleCopyAudit.class))).thenAnswer(invocation -> {
            SysRoleCopyAudit audit = invocation.getArgument(0);
            audit.setId(501L);
            return audit;
        });
        RoleDTO savedDto = RoleDTO.builder()
                .id(99L).roleCode("WAREHOUSE_COPY").roleType("CUSTOM").status("DISABLED").build();
        when(roleService.getRoleById(99L)).thenReturn(savedDto);

        var result = service.copy(7L, request, 3L, "admin");

        assertThat(result.getRole()).isSameAs(savedDto);
        assertThat(result.getPermissionCount()).isEqualTo(2);
        assertThat(result.getHighRiskCount()).isEqualTo(1);
        assertThat(result.getAuditId()).isEqualTo(501L);

        ArgumentCaptor<SysRole> roleCaptor = ArgumentCaptor.forClass(SysRole.class);
        verify(roleRepository).saveAndFlush(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getRoleType()).isEqualTo(SysRole.ROLE_TYPE_CUSTOM);
        assertThat(roleCaptor.getValue().getStatus()).isEqualTo("DISABLED");
        assertThat(roleCaptor.getValue().getReviewStatus()).isEqualTo(SysRole.REVIEW_STATUS_DRAFT);
        assertThat(roleCaptor.getValue().getApprovalTemplateCode()).isEqualTo(SysRole.SIMPLE_APPROVAL_TEMPLATE_CODE);
        assertThat(roleCaptor.getValue().getParentRoles()).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SysRolePermission>> permissionCaptor = ArgumentCaptor.forClass(List.class);
        verify(rolePermissionRepository).saveAll(permissionCaptor.capture());
        assertThat(permissionCaptor.getValue())
                .extracting(SysRolePermission::getPermissionId)
                .containsExactlyInAnyOrder(10L, 11L);

        ArgumentCaptor<SysRoleCopyAudit> auditCaptor = ArgumentCaptor.forClass(SysRoleCopyAudit.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getOperationReason())
                .isEqualTo("Temporary warehouse team baseline");
        assertThat(auditCaptor.getValue().getHighRiskPermissionCodes()).isEqualTo("outbound:pick");
    }

    private void arrangeSnapshot(List<PermissionDTO> permissions) {
        when(roleRepository.findById(7L)).thenReturn(Optional.of(source));
        when(dynamicPermissionService.getInheritedRoleIds(Set.of(7L))).thenReturn(Set.of(7L));
        when(dynamicPermissionService.getPermissionsByRoleIds(Set.of(7L))).thenReturn(permissions);
    }

    private RoleCopyRequest validRequest(String fingerprint) {
        return RoleCopyRequest.builder()
                .roleCode("warehouse_copy")
                .roleName("Warehouse Copy")
                .description("Independent warehouse permission package")
                .snapshotFingerprint(fingerprint)
                .build();
    }

    private PermissionDTO permission(Long id, String code, String risk, boolean assignable) {
        return PermissionDTO.builder()
                .id(id)
                .permissionCode(code)
                .permissionName(code)
                .permissionType("API")
                .riskLevel(risk)
                .customAssignable(assignable)
                .status("ACTIVE")
                .sortOrder(id.intValue())
                .build();
    }
}

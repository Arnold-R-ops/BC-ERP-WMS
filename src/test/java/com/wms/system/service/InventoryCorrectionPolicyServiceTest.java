package com.wms.system.service;

import com.wms.system.dto.v45.EmergencyStockCorrectionPolicyResponse;
import com.wms.system.entity.InventoryCorrectionPolicyAudit;
import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.EmergencyCorrectionApprovalMode;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryCorrectionPolicyAuditRepository;
import com.wms.system.repository.SystemConfigRepository;
import com.wms.system.subscription.service.SubscriptionEntitlementService;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryCorrectionPolicyServiceTest {

    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private InventoryCorrectionPolicyAuditRepository auditRepository;
    @Mock private SubscriptionEntitlementService entitlementService;

    private InventoryCorrectionPolicyService service;

    @BeforeEach
    void setUp() {
        service = new InventoryCorrectionPolicyService(
            systemConfigRepository,
            auditRepository,
            entitlementService
        );
        TenantContextHolder.set(new TenantContext(
            42L,
            "company-42",
            "company-42.example.test",
            RequestSurface.TENANT,
            TenantStatus.ACTIVE
        ));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void approvalIsOffByDefault() {
        when(systemConfigRepository.findByConfigKey(InventoryCorrectionPolicyService.CONFIG_KEY))
            .thenReturn(Optional.empty());
        when(entitlementService.hasEntitlement(
            InventoryCorrectionPolicyService.APPROVAL_ENTITLEMENT
        )).thenReturn(false);

        EmergencyStockCorrectionPolicyResponse policy = service.getPolicy();

        assertThat(policy.getConfiguredMode()).isEqualTo(EmergencyCorrectionApprovalMode.NONE);
        assertThat(policy.getEffectiveMode()).isEqualTo(EmergencyCorrectionApprovalMode.NONE);
        assertThat(policy.isApprovalEntitled()).isFalse();
        assertThat(policy.isConfigurable()).isFalse();
        assertThat(policy.getAvailableModes())
            .containsExactly(EmergencyCorrectionApprovalMode.NONE);
    }

    @Test
    void entitlementLossForcesNewCorrectionsToNoneWithoutErasingConfiguredMode() {
        SystemConfig config = config(EmergencyCorrectionApprovalMode.THREE_PERSON);
        when(systemConfigRepository.findByConfigKey(InventoryCorrectionPolicyService.CONFIG_KEY))
            .thenReturn(Optional.of(config));
        when(entitlementService.hasEntitlement(
            InventoryCorrectionPolicyService.APPROVAL_ENTITLEMENT
        )).thenReturn(false);

        EmergencyStockCorrectionPolicyResponse policy = service.getPolicy();

        assertThat(policy.getConfiguredMode())
            .isEqualTo(EmergencyCorrectionApprovalMode.THREE_PERSON);
        assertThat(policy.getEffectiveMode()).isEqualTo(EmergencyCorrectionApprovalMode.NONE);
        assertThat(service.effectiveMode()).isEqualTo(EmergencyCorrectionApprovalMode.NONE);
    }

    @Test
    void tenantWithoutEntitlementCannotEnableApproval() {
        when(entitlementService.hasEntitlement(
            InventoryCorrectionPolicyService.APPROVAL_ENTITLEMENT
        )).thenReturn(false);

        assertThatThrownBy(() -> service.updatePolicy(
            EmergencyCorrectionApprovalMode.TWO_PERSON,
            "enable two-person review",
            21L,
            "admin"
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorKey())
                .isEqualTo(ErrorKeys.INVENTORY_CORRECTION_APPROVAL_NOT_ENTITLED));
        verify(systemConfigRepository, never()).save(any());
        verifyNoInteractions(auditRepository);
    }

    @Test
    void entitledTenantCanChooseModeAndChangeIsAudited() {
        SystemConfig config = config(EmergencyCorrectionApprovalMode.NONE);
        when(entitlementService.hasEntitlement(
            InventoryCorrectionPolicyService.APPROVAL_ENTITLEMENT
        )).thenReturn(true);
        when(systemConfigRepository.findByConfigKeyForUpdate(
            InventoryCorrectionPolicyService.CONFIG_KEY
        )).thenReturn(Optional.of(config));
        when(systemConfigRepository.save(config)).thenReturn(config);

        EmergencyStockCorrectionPolicyResponse policy = service.updatePolicy(
            EmergencyCorrectionApprovalMode.THREE_PERSON,
            "separate maker reviewer and approver",
            21L,
            "admin"
        );

        assertThat(policy.getEffectiveMode())
            .isEqualTo(EmergencyCorrectionApprovalMode.THREE_PERSON);
        assertThat(config.getConfigValue()).isEqualTo("THREE_PERSON");
        ArgumentCaptor<InventoryCorrectionPolicyAudit> audit =
            ArgumentCaptor.forClass(InventoryCorrectionPolicyAudit.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getCompanyId()).isEqualTo(42L);
        assertThat(audit.getValue().getOldMode()).isEqualTo(EmergencyCorrectionApprovalMode.NONE);
        assertThat(audit.getValue().getNewMode())
            .isEqualTo(EmergencyCorrectionApprovalMode.THREE_PERSON);
        assertThat(audit.getValue().getReason())
            .isEqualTo("separate maker reviewer and approver");
    }

    private SystemConfig config(EmergencyCorrectionApprovalMode mode) {
        return SystemConfig.builder()
            .id(1L)
            .configKey(InventoryCorrectionPolicyService.CONFIG_KEY)
            .configValue(mode.name())
            .configType(SystemConfig.ConfigType.STRING.name())
            .build();
    }
}

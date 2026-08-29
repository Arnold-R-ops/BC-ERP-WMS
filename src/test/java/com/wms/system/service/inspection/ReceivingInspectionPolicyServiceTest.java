package com.wms.system.service.inspection;

import com.wms.system.dto.receivinginspection.UpdateReceivingInspectionPolicyRequest;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.*;
import com.wms.system.repository.SystemConfigRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceivingInspectionPolicyServiceTest {
    @Mock SystemConfigRepository repository;
    ReceivingInspectionPolicyService service;

    @BeforeEach
    void setUp() {
        service = new ReceivingInspectionPolicyService(repository);
    }

    @Test
    void ordinaryTenantDefaultsToNoInspectionAndDirectDecision() {
        when(repository.findByConfigKey(anyString())).thenReturn(Optional.empty());

        var policy = service.getPolicy();

        assertThat(policy.defaultInspectionRequired()).isFalse();
        assertThat(policy.reviewMode()).isEqualTo(ReceivingInspectionReviewMode.DIRECT);
        assertThat(policy.skuRequirements()).containsExactly(
            ReceivingInspectionRequirement.INHERIT,
            ReceivingInspectionRequirement.REQUIRED,
            ReceivingInspectionRequirement.EXEMPT);
    }

    @Test
    void skuOverrideWinsOverTenantDefault() {
        when(repository.findByConfigKey(ReceivingInspectionPolicyService.DEFAULT_REQUIRED_KEY))
            .thenReturn(Optional.of(SystemConfig.builder()
                .configKey(ReceivingInspectionPolicyService.DEFAULT_REQUIRED_KEY)
                .configValue("true").configType("BOOLEAN").build()));
        ProductSku exempt = ProductSku.builder()
            .inspectionRequirement(ReceivingInspectionRequirement.EXEMPT).build();
        ProductSku required = ProductSku.builder()
            .inspectionRequirement(ReceivingInspectionRequirement.REQUIRED).build();
        ProductSku inherited = ProductSku.builder()
            .inspectionRequirement(ReceivingInspectionRequirement.INHERIT).build();

        assertThat(service.requiresInspection(exempt)).isFalse();
        assertThat(service.requiresInspection(required)).isTrue();
        assertThat(service.requiresInspection(inherited)).isTrue();
    }

    @Test
    void tenantCanEnableInspectionAndReviewWithoutPackageCoupling() {
        when(repository.findByConfigKeyForUpdate(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByConfigKey(ReceivingInspectionPolicyService.DEFAULT_REQUIRED_KEY))
            .thenReturn(Optional.of(SystemConfig.builder().configKey("required")
                .configValue("true").configType("BOOLEAN").build()));
        when(repository.findByConfigKey(ReceivingInspectionPolicyService.REVIEW_MODE_KEY))
            .thenReturn(Optional.of(SystemConfig.builder().configKey("review")
                .configValue("REVIEW_REQUIRED").configType("STRING").build()));

        var response = service.update(new UpdateReceivingInspectionPolicyRequest(
            true, ReceivingInspectionReviewMode.REVIEW_REQUIRED, "regulated goods"),
            7L, "admin");

        assertThat(response.defaultInspectionRequired()).isTrue();
        assertThat(response.reviewMode())
            .isEqualTo(ReceivingInspectionReviewMode.REVIEW_REQUIRED);
        verify(repository, times(2)).save(any(SystemConfig.class));
    }
}

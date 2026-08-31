package com.wms.system.service.rma;

import com.wms.system.dto.rma.UpdateRmaPolicyRequest;
import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.RmaApprovalMode;
import com.wms.system.repository.SystemConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RmaPolicyServiceTest {
    @Mock SystemConfigRepository repository;

    @Test
    void ordinaryTenantDefaultsToFiveDaysDirectAndRiskFeaturesOff() {
        when(repository.findByConfigKey(any())).thenReturn(Optional.empty());

        var response = new RmaPolicyService(repository).get();

        assertThat(response.approvalMode()).isEqualTo(RmaApprovalMode.DIRECT);
        assertThat(response.standardReturnDays()).isEqualTo(5);
        assertThat(response.refundOnlyEnabled()).isFalse();
        assertThat(response.advanceResolutionEnabled()).isFalse();
        assertThat(response.quarantineStagingEnabled()).isFalse();
    }

    @Test
    void tenantCanSwitchApprovalAndOptionalFeaturesWithoutPackageCoupling() {
        Map<String, SystemConfig> values = new HashMap<>();
        when(repository.findByConfigKeyForUpdate(any())).thenReturn(Optional.empty());
        when(repository.save(any(SystemConfig.class))).thenAnswer(invocation -> {
            SystemConfig config = invocation.getArgument(0);
            values.put(config.getConfigKey(), config);
            return config;
        });
        when(repository.findByConfigKey(any())).thenAnswer(invocation ->
            Optional.ofNullable(values.get(invocation.getArgument(0))));

        var response = new RmaPolicyService(repository).update(new UpdateRmaPolicyRequest(
            RmaApprovalMode.RISK_BASED, 14, true, true, true, "controlled returns"),
            7L, "admin");

        assertThat(response.approvalMode()).isEqualTo(RmaApprovalMode.RISK_BASED);
        assertThat(response.standardReturnDays()).isEqualTo(14);
        assertThat(response.refundOnlyEnabled()).isTrue();
        assertThat(response.advanceResolutionEnabled()).isTrue();
        assertThat(response.quarantineStagingEnabled()).isTrue();
        assertThat(values.values()).extracting(SystemConfig::getDescription)
            .allMatch(description -> description.contains("7:admin")
                && description.contains("controlled returns"));
    }
}

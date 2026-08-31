package com.wms.system.service;

import com.wms.system.dto.sales.UpdateSalesOrderPolicyRequest;
import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.SalesApprovalMode;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SystemConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderPolicyServiceTest {

    @Mock
    private SystemConfigRepository repository;

    @Test
    void missingConfigurationUsesOrdinaryTenantDefaults() {
        SalesOrderPolicyService service = new SalesOrderPolicyService(repository);

        assertThat(service.approvalMode()).isEqualTo(SalesApprovalMode.DIRECT);
        assertThat(service.approvalAmountThreshold()).isEqualByComparingTo("50000.00");
        assertThat(service.detailedLogisticsEnabled()).isFalse();
    }

    @Test
    void invalidApprovalModeFailsClosed() {
        when(repository.findByConfigKey(SalesOrderPolicyService.APPROVAL_MODE_KEY))
            .thenReturn(Optional.of(config(SalesOrderPolicyService.APPROVAL_MODE_KEY, "unknown")));

        assertThatThrownBy(() -> new SalesOrderPolicyService(repository).approvalMode())
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.VALIDATION_FAILED);
    }

    @Test
    void updatePersistsIndependentApprovalAndLogisticsSettingsWithAuditDescription() {
        Map<String, SystemConfig> persisted = new HashMap<>();
        when(repository.findByConfigKeyForUpdate(any())).thenReturn(Optional.empty());
        when(repository.save(any(SystemConfig.class))).thenAnswer(invocation -> {
            SystemConfig value = invocation.getArgument(0);
            persisted.put(value.getConfigKey(), value);
            return value;
        });
        when(repository.findByConfigKey(any())).thenAnswer(invocation ->
            Optional.ofNullable(persisted.get(invocation.getArgument(0))));

        SalesOrderPolicyService service = new SalesOrderPolicyService(repository);
        var response = service.update(new UpdateSalesOrderPolicyRequest(
            SalesApprovalMode.ALWAYS, new BigDecimal("2500.00"), true, "Enable controlled flow"
        ), 9L, "tenant-admin");

        assertThat(response.approvalMode()).isEqualTo(SalesApprovalMode.ALWAYS);
        assertThat(response.approvalAmountThreshold()).isEqualByComparingTo("2500.00");
        assertThat(response.detailedLogisticsEnabled()).isTrue();
        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(repository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(SystemConfig::getDescription)
            .allMatch(description -> description.contains("9:tenant-admin")
                && description.contains("Enable controlled flow"));
    }

    private SystemConfig config(String key, String value) {
        return SystemConfig.builder()
            .configKey(key)
            .configValue(value)
            .configType(SystemConfig.ConfigType.STRING.name())
            .build();
    }
}

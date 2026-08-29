package com.wms.system.service.inventory;

import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.InventoryQualityReleaseMode;
import com.wms.system.repository.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryQualityPolicyServiceTest {
    @Mock private SystemConfigRepository repository;
    private InventoryQualityPolicyService service;

    @BeforeEach
    void setUp() {
        service = new InventoryQualityPolicyService(repository);
    }

    @Test
    void defaultsToDirectWithReasonWithoutPackageEntitlement() {
        when(repository.findByConfigKey(InventoryQualityPolicyService.RELEASE_MODE_KEY))
            .thenReturn(Optional.empty());

        assertThat(service.getPolicy().releaseMode())
            .isEqualTo(InventoryQualityReleaseMode.DIRECT_WITH_REASON);
        assertThat(service.getPolicy().availableModes())
            .containsExactly(InventoryQualityReleaseMode.DIRECT_WITH_REASON,
                InventoryQualityReleaseMode.APPROVAL_REQUIRED);
    }

    @Test
    void tenantCanEnableApprovalIndependently() {
        when(repository.findByConfigKeyForUpdate(InventoryQualityPolicyService.RELEASE_MODE_KEY))
            .thenReturn(Optional.empty());
        when(repository.save(any(SystemConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByConfigKey(InventoryQualityPolicyService.RELEASE_MODE_KEY))
            .thenReturn(Optional.of(SystemConfig.builder()
                .configKey(InventoryQualityPolicyService.RELEASE_MODE_KEY)
                .configValue("APPROVAL_REQUIRED")
                .configType("STRING")
                .build()));

        var response = service.update(
            InventoryQualityReleaseMode.APPROVAL_REQUIRED,
            "enable maker-checker",
            7L,
            "tenant-admin"
        );

        assertThat(response.releaseMode()).isEqualTo(InventoryQualityReleaseMode.APPROVAL_REQUIRED);
        verify(repository).save(any(SystemConfig.class));
    }
}

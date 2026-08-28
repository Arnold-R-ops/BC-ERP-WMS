package com.wms.system.service;

import com.wms.system.entity.enums.OtherInboundBusinessType;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtherInboundFeaturePolicyServiceTest {
    @Mock private SystemConfigRepository systemConfigRepository;

    private OtherInboundFeaturePolicyService service;

    @BeforeEach
    void setUp() {
        service = new OtherInboundFeaturePolicyService(systemConfigRepository);
    }

    @Test
    void featureIsClosedByDefault() {
        when(systemConfigRepository.findByConfigKey(any())).thenReturn(Optional.empty());

        assertThat(service.getPolicy().isEnabled()).isFalse();
        assertThatThrownBy(() -> service.requireEnabled(OtherInboundBusinessType.FREE_SAMPLE))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void administratorMustConfigureAtLeastOneControlledType() {
        assertThatThrownBy(() -> service.updatePolicy(true, Set.of(), "enable", 1L, "admin"))
            .isInstanceOf(BusinessException.class);
    }
}

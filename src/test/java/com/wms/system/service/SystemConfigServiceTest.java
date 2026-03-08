package com.wms.system.service;

import com.wms.system.entity.SystemConfig;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.SystemConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemConfigService Tests")
class SystemConfigServiceTest {

    @Mock private SystemConfigRepository systemConfigRepository;

    @InjectMocks
    private SystemConfigService systemConfigService;

    // ========== getConfigValue ==========

    @Test
    @DisplayName("getConfigValue - returns value when key exists")
    void testGetConfigValue_Success() {
        SystemConfig config = SystemConfig.builder()
                .configKey("MAX_CREDIT_DAYS")
                .configValue("30")
                .build();

        when(systemConfigRepository.findByConfigKey("MAX_CREDIT_DAYS"))
                .thenReturn(Optional.of(config));

        String result = systemConfigService.getConfigValue("MAX_CREDIT_DAYS");

        assertThat(result).isEqualTo("30");
    }

    @Test
    @DisplayName("getConfigValue - throws BusinessException when key not found")
    void testGetConfigValue_NotFound() {
        when(systemConfigRepository.findByConfigKey("NONEXISTENT"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemConfigService.getConfigValue("NONEXISTENT"))
                .isInstanceOf(BusinessException.class);
    }

    // ========== getDecimalConfig ==========

    @Test
    @DisplayName("getDecimalConfig - returns BigDecimal from string config")
    void testGetDecimalConfig_Success() {
        SystemConfig config = SystemConfig.builder()
                .configKey("RISK_THRESHOLD")
                .configValue("0.85")
                .build();

        when(systemConfigRepository.findByConfigKey("RISK_THRESHOLD"))
                .thenReturn(Optional.of(config));

        BigDecimal result = systemConfigService.getDecimalConfig("RISK_THRESHOLD");

        assertThat(result).isEqualByComparingTo("0.85");
    }

    @Test
    @DisplayName("getDecimalConfig - throws BusinessException when value is not a decimal")
    void testGetDecimalConfig_InvalidType() {
        SystemConfig config = SystemConfig.builder()
                .configKey("SOME_KEY")
                .configValue("not_a_number")
                .build();

        when(systemConfigRepository.findByConfigKey("SOME_KEY"))
                .thenReturn(Optional.of(config));

        assertThatThrownBy(() -> systemConfigService.getDecimalConfig("SOME_KEY"))
                .isInstanceOf(BusinessException.class);
    }

    // ========== getIntegerConfig ==========

    @Test
    @DisplayName("getIntegerConfig - returns Integer from string config")
    void testGetIntegerConfig_Success() {
        SystemConfig config = SystemConfig.builder()
                .configKey("NEAR_EXPIRY_DAYS")
                .configValue("7")
                .build();

        when(systemConfigRepository.findByConfigKey("NEAR_EXPIRY_DAYS"))
                .thenReturn(Optional.of(config));

        Integer result = systemConfigService.getIntegerConfig("NEAR_EXPIRY_DAYS");

        assertThat(result).isEqualTo(7);
    }

    @Test
    @DisplayName("getIntegerConfig - throws BusinessException when value is not an integer")
    void testGetIntegerConfig_InvalidType() {
        SystemConfig config = SystemConfig.builder()
                .configKey("INT_KEY")
                .configValue("3.14")
                .build();

        when(systemConfigRepository.findByConfigKey("INT_KEY"))
                .thenReturn(Optional.of(config));

        assertThatThrownBy(() -> systemConfigService.getIntegerConfig("INT_KEY"))
                .isInstanceOf(BusinessException.class);
    }

    // ========== getBooleanConfig ==========

    @Test
    @DisplayName("getBooleanConfig - returns true when value is 'true'")
    void testGetBooleanConfig_True() {
        SystemConfig config = SystemConfig.builder()
                .configKey("ENABLE_RISK_CHECK")
                .configValue("true")
                .build();

        when(systemConfigRepository.findByConfigKey("ENABLE_RISK_CHECK"))
                .thenReturn(Optional.of(config));

        Boolean result = systemConfigService.getBooleanConfig("ENABLE_RISK_CHECK");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("getBooleanConfig - returns false when value is 'false'")
    void testGetBooleanConfig_False() {
        SystemConfig config = SystemConfig.builder()
                .configKey("ENABLE_RISK_CHECK")
                .configValue("false")
                .build();

        when(systemConfigRepository.findByConfigKey("ENABLE_RISK_CHECK"))
                .thenReturn(Optional.of(config));

        Boolean result = systemConfigService.getBooleanConfig("ENABLE_RISK_CHECK");

        assertThat(result).isFalse();
    }

    // ========== updateConfigValue ==========

    @Test
    @DisplayName("updateConfigValue - updates and evicts cache")
    void testUpdateConfigValue_Success() {
        SystemConfig config = SystemConfig.builder()
                .configKey("MAX_CREDIT_DAYS")
                .configValue("30")
                .build();

        when(systemConfigRepository.findByConfigKey("MAX_CREDIT_DAYS"))
                .thenReturn(Optional.of(config));
        when(systemConfigRepository.save(any(SystemConfig.class))).thenReturn(config);

        systemConfigService.updateConfigValue("MAX_CREDIT_DAYS", "60");

        verify(systemConfigRepository).save(argThat(c -> "60".equals(c.getConfigValue())));
    }

    @Test
    @DisplayName("updateConfigValue - throws when key not found")
    void testUpdateConfigValue_NotFound() {
        when(systemConfigRepository.findByConfigKey("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemConfigService.updateConfigValue("MISSING", "123"))
                .isInstanceOf(BusinessException.class);
    }
}

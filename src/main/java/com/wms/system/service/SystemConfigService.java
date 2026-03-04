package com.wms.system.service;

import com.wms.system.entity.SystemConfig;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 系统配置服务
 *
 * V3.7 架构：系统配置管理
 *
 * 核心功能：
 * 1. 获取配置值（支持类型转换）
 * 2. 更新配置值
 * 3. 缓存配置值（提高性能）
 *
 * 配置类型：
 * - DECIMAL: 小数类型（如金额、比例）
 * - INTEGER: 整数类型（如天数、数量）
 * - STRING: 字符串类型（如名称、描述）
 * - BOOLEAN: 布尔类型（如开关、标志）
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    /**
     * 获取配置值（字符串）
     *
     * @param configKey 配置键
     * @return 配置值
     * @throws BusinessException 如果配置不存在
     */
    @Cacheable(value = "systemConfig", key = "#configKey")
    public String getConfigValue(String configKey) {
        log.debug("获取系统配置: {}", configKey);
        SystemConfig config = systemConfigRepository.findByConfigKey(configKey)
                .orElseThrow(() -> new BusinessException(
                        ErrorKeys.SYSTEM_CONFIG_NOT_FOUND,
                        Map.of("configKey", configKey)
                ));
        return config.getConfigValue();
    }

    /**
     * 获取配置值（小数类型）
     *
     * @param configKey 配置键
     * @return 配置值（BigDecimal）
     * @throws BusinessException 如果配置不存在或类型不匹配
     */
    public BigDecimal getDecimalConfig(String configKey) {
        String value = getConfigValue(configKey);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.error("配置值类型转换失败: {} = {}", configKey, value, e);
            throw new BusinessException(
                    ErrorKeys.SYSTEM_CONFIG_INVALID_TYPE,
                    Map.of(
                            "configKey", configKey,
                            "expectedType", "DECIMAL",
                            "actualValue", value
                    )
            );
        }
    }

    /**
     * 获取配置值（整数类型）
     *
     * @param configKey 配置键
     * @return 配置值（Integer）
     * @throws BusinessException 如果配置不存在或类型不匹配
     */
    public Integer getIntegerConfig(String configKey) {
        String value = getConfigValue(configKey);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.error("配置值类型转换失败: {} = {}", configKey, value, e);
            throw new BusinessException(
                    ErrorKeys.SYSTEM_CONFIG_INVALID_TYPE,
                    Map.of(
                            "configKey", configKey,
                            "expectedType", "INTEGER",
                            "actualValue", value
                    )
            );
        }
    }

    /**
     * 获取配置值（布尔类型）
     *
     * @param configKey 配置键
     * @return 配置值（Boolean）
     * @throws BusinessException 如果配置不存在
     */
    public Boolean getBooleanConfig(String configKey) {
        String value = getConfigValue(configKey);
        return Boolean.parseBoolean(value);
    }

    /**
     * 获取配置值（字符串类型）
     *
     * @param configKey 配置键
     * @return 配置值（String）
     * @throws BusinessException 如果配置不存在
     */
    public String getStringConfig(String configKey) {
        return getConfigValue(configKey);
    }

    /**
     * 更新配置值
     *
     * @param configKey 配置键
     * @param configValue 配置值
     * @throws BusinessException 如果配置不存在
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "systemConfig", key = "#configKey")
    public void updateConfigValue(String configKey, String configValue) {
        log.info("更新系统配置: {} = {}", configKey, configValue);
        SystemConfig config = systemConfigRepository.findByConfigKey(configKey)
                .orElseThrow(() -> new BusinessException(
                        ErrorKeys.SYSTEM_CONFIG_NOT_FOUND,
                        Map.of("configKey", configKey)
                ));

        config.setConfigValue(configValue);
        systemConfigRepository.save(config);
        log.info("系统配置更新成功: {} = {}", configKey, configValue);
    }

    /**
     * 获取销售订单审批金额阈值
     *
     * 说明：
     * - 订单总额超过此阈值时，触发审批流程
     * - 默认值：50000.00
     *
     * @return 审批金额阈值
     */
    public BigDecimal getSalesApprovalAmountThreshold() {
        return getDecimalConfig("sales.approval.amount_threshold");
    }

    /**
     * 创建配置（如果不存在）
     *
     * @param configKey 配置键
     * @param configValue 配置值
     * @param description 配置描述
     * @param configType 配置类型
     */
    @Transactional(rollbackFor = Exception.class)
    public void createConfigIfNotExists(String configKey, String configValue, String description, String configType) {
        if (!systemConfigRepository.existsByConfigKey(configKey)) {
            SystemConfig config = SystemConfig.builder()
                    .configKey(configKey)
                    .configValue(configValue)
                    .description(description)
                    .configType(configType)
                    .build();
            systemConfigRepository.save(config);
            log.info("创建系统配置: {} = {}", configKey, configValue);
        }
    }
}

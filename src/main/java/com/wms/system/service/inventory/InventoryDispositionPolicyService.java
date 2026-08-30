package com.wms.system.service.inventory;

import com.wms.system.dto.inventorydisposition.*;
import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.InventoryDispositionReviewMode;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryDispositionPolicyService {
    public static final String REVIEW_MODE_KEY = "inventory.disposition.review.mode";
    public static final String AUTO_EXPIRY_KEY = "inventory.disposition.auto_expiry.enabled";
    public static final String BUSINESS_TIMEZONE_KEY = "inventory.disposition.business_timezone";
    public static final String DEFAULT_TIMEZONE = "Europe/London";

    private final SystemConfigRepository configRepository;

    @Transactional(readOnly = true)
    public InventoryDispositionPolicyResponse getPolicy() {
        return new InventoryDispositionPolicyResponse(
            reviewMode(), automaticExpiryEnabled(), businessTimezone(),
            List.of(InventoryDispositionReviewMode.DIRECT_WITH_REASON,
                InventoryDispositionReviewMode.APPROVAL_REQUIRED)
        );
    }

    @Transactional(readOnly = true)
    public InventoryDispositionReviewMode reviewMode() {
        return configRepository.findByConfigKey(REVIEW_MODE_KEY)
            .map(SystemConfig::getConfigValue)
            .map(this::parseMode)
            .orElse(InventoryDispositionReviewMode.DIRECT_WITH_REASON);
    }

    @Transactional(readOnly = true)
    public boolean automaticExpiryEnabled() {
        return configRepository.findByConfigKey(AUTO_EXPIRY_KEY)
            .map(SystemConfig::getConfigValue)
            .map(String::trim)
            .map(Boolean::parseBoolean)
            .orElse(true);
    }

    @Transactional(readOnly = true)
    public String businessTimezone() {
        String configured = configRepository.findByConfigKey(BUSINESS_TIMEZONE_KEY)
            .map(SystemConfig::getConfigValue)
            .filter(value -> !value.isBlank())
            .orElse(DEFAULT_TIMEZONE);
        return validateTimezone(configured);
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryDispositionPolicyResponse update(
        UpdateInventoryDispositionPolicyRequest request,
        Long actorId,
        String actorName
    ) {
        String timezone = validateTimezone(request.businessTimezone().trim());
        String description = "Changed by " + actorId + ":" + actorName + "; " + request.reason().trim();
        save(REVIEW_MODE_KEY, request.reviewMode().name(), SystemConfig.ConfigType.STRING, description);
        save(AUTO_EXPIRY_KEY, Boolean.toString(request.automaticExpiryEnabled()),
            SystemConfig.ConfigType.BOOLEAN, description);
        save(BUSINESS_TIMEZONE_KEY, timezone, SystemConfig.ConfigType.STRING, description);
        return getPolicy();
    }

    private void save(String key, String value, SystemConfig.ConfigType type, String description) {
        SystemConfig config = configRepository.findByConfigKeyForUpdate(key)
            .orElseGet(() -> SystemConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        config.setConfigType(type.name());
        config.setDescription(description);
        configRepository.save(config);
    }

    private InventoryDispositionReviewMode parseMode(String value) {
        try {
            return InventoryDispositionReviewMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw policyInvalid("reviewMode", value);
        }
    }

    private String validateTimezone(String value) {
        try {
            return ZoneId.of(value).getId();
        } catch (DateTimeException exception) {
            throw policyInvalid("businessTimezone", value);
        }
    }

    private BusinessException policyInvalid(String field, String value) {
        return new BusinessException(ErrorKeys.INVENTORY_DISPOSITION_POLICY_INVALID,
            Map.of("field", field, "configuredValue", value));
    }
}

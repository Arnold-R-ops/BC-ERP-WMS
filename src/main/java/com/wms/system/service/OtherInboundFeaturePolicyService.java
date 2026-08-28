package com.wms.system.service;

import com.wms.system.dto.inbound.OtherInboundFeaturePolicyResponse;
import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.OtherInboundBusinessType;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OtherInboundFeaturePolicyService {
    public static final String ENABLED_KEY = "inventory.other_inbound.enabled";
    public static final String TYPES_KEY = "inventory.other_inbound.allowed_types";

    private final SystemConfigRepository systemConfigRepository;

    @Transactional(readOnly = true)
    public OtherInboundFeaturePolicyResponse getPolicy() {
        boolean enabled = systemConfigRepository.findByConfigKey(ENABLED_KEY)
            .map(SystemConfig::getConfigValue)
            .map(Boolean::parseBoolean)
            .orElse(false);
        Set<OtherInboundBusinessType> types = systemConfigRepository.findByConfigKey(TYPES_KEY)
            .map(SystemConfig::getConfigValue)
            .map(this::parseTypes)
            .orElseGet(() -> EnumSet.of(OtherInboundBusinessType.FREE_SAMPLE));
        return OtherInboundFeaturePolicyResponse.builder()
            .enabled(enabled)
            .allowedBusinessTypes(Set.copyOf(types))
            .build();
    }

    @Transactional(readOnly = true)
    public void requireEnabled(OtherInboundBusinessType type) {
        OtherInboundFeaturePolicyResponse policy = getPolicy();
        if (!policy.isEnabled()) {
            throw new BusinessException("OTHER_INBOUND_DISABLED", Map.of());
        }
        if (!policy.getAllowedBusinessTypes().contains(type)) {
            throw new BusinessException(
                "OTHER_INBOUND_TYPE_DISABLED",
                Map.of("businessType", type.name())
            );
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public OtherInboundFeaturePolicyResponse updatePolicy(
        boolean enabled,
        Set<OtherInboundBusinessType> types,
        String reason,
        Long actorId,
        String actorName
    ) {
        if (types == null || types.isEmpty()) {
            throw new BusinessException(
                "OTHER_INBOUND_TYPES_REQUIRED",
                Map.of("reason", "At least one controlled business type is required")
            );
        }
        upsert(ENABLED_KEY, Boolean.toString(enabled),
            "Tenant-admin switch for controlled other-inbound applications");
        upsert(TYPES_KEY, types.stream().map(Enum::name).sorted()
            .collect(Collectors.joining(",")), "Allowed other-inbound business types");
        upsert("inventory.other_inbound.last_change", actorId + ":" + actorName,
            reason.trim());
        return getPolicy();
    }

    private void upsert(String key, String value, String description) {
        SystemConfig config = systemConfigRepository.findByConfigKeyForUpdate(key)
            .orElseGet(() -> SystemConfig.builder()
                .configKey(key)
                .configType(SystemConfig.ConfigType.STRING.name())
                .build());
        config.setConfigValue(value);
        config.setDescription(description);
        systemConfigRepository.save(config);
    }

    private Set<OtherInboundBusinessType> parseTypes(String value) {
        try {
            return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(token -> OtherInboundBusinessType.valueOf(token.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(OtherInboundBusinessType.class)));
        } catch (RuntimeException exception) {
            throw new BusinessException(
                "OTHER_INBOUND_POLICY_INVALID",
                Map.of("configuredValue", value)
            );
        }
    }
}

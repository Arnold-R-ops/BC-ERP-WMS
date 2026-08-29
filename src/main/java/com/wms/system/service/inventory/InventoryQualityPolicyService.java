package com.wms.system.service.inventory;

import com.wms.system.dto.inventoryquality.InventoryQualityPolicyResponse;
import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.InventoryQualityReleaseMode;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryQualityPolicyService {
    public static final String RELEASE_MODE_KEY = "inventory.quality.release.mode";

    private final SystemConfigRepository systemConfigRepository;

    @Transactional(readOnly = true)
    public InventoryQualityPolicyResponse getPolicy() {
        return new InventoryQualityPolicyResponse(
            releaseMode(),
            List.of(InventoryQualityReleaseMode.DIRECT_WITH_REASON,
                InventoryQualityReleaseMode.APPROVAL_REQUIRED)
        );
    }

    @Transactional(readOnly = true)
    public InventoryQualityReleaseMode releaseMode() {
        return systemConfigRepository.findByConfigKey(RELEASE_MODE_KEY)
            .map(SystemConfig::getConfigValue)
            .map(this::parse)
            .orElse(InventoryQualityReleaseMode.DIRECT_WITH_REASON);
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryQualityPolicyResponse update(
        InventoryQualityReleaseMode mode,
        String reason,
        Long actorId,
        String actorName
    ) {
        SystemConfig config = systemConfigRepository.findByConfigKeyForUpdate(RELEASE_MODE_KEY)
            .orElseGet(() -> SystemConfig.builder()
                .configKey(RELEASE_MODE_KEY)
                .configType(SystemConfig.ConfigType.STRING.name())
                .build());
        config.setConfigValue(mode.name());
        config.setDescription("Changed by " + actorId + ":" + actorName + "; " + reason.trim());
        systemConfigRepository.save(config);
        return getPolicy();
    }

    private InventoryQualityReleaseMode parse(String value) {
        try {
            return InventoryQualityReleaseMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new BusinessException(
                ErrorKeys.INVENTORY_QUALITY_ACTION_INVALID,
                Map.of("reason", "RELEASE_POLICY_INVALID", "configuredValue", value)
            );
        }
    }
}

package com.wms.system.service.inspection;

import com.wms.system.dto.receivinginspection.*;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.*;
import com.wms.system.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ReceivingInspectionPolicyService {
    public static final String DEFAULT_REQUIRED_KEY = "receiving.inspection.default.required";
    public static final String REVIEW_MODE_KEY = "receiving.inspection.review.mode";

    private final SystemConfigRepository configRepository;

    @Transactional(readOnly = true)
    public ReceivingInspectionPolicyResponse getPolicy() {
        return new ReceivingInspectionPolicyResponse(
            defaultRequired(), reviewMode(),
            List.of(ReceivingInspectionRequirement.values()),
            List.of(ReceivingInspectionReviewMode.values())
        );
    }

    @Transactional(readOnly = true)
    public boolean requiresInspection(ProductSku sku) {
        ReceivingInspectionRequirement requirement = sku.getInspectionRequirement() == null
            ? ReceivingInspectionRequirement.INHERIT : sku.getInspectionRequirement();
        return switch (requirement) {
            case REQUIRED -> true;
            case EXEMPT -> false;
            case INHERIT -> defaultRequired();
        };
    }

    @Transactional(readOnly = true)
    public boolean defaultRequired() {
        return configRepository.findByConfigKey(DEFAULT_REQUIRED_KEY)
            .map(SystemConfig::getConfigValue)
            .map(Boolean::parseBoolean)
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public ReceivingInspectionReviewMode reviewMode() {
        return configRepository.findByConfigKey(REVIEW_MODE_KEY)
            .map(SystemConfig::getConfigValue)
            .map(value -> ReceivingInspectionReviewMode.valueOf(
                value.trim().toUpperCase(Locale.ROOT)))
            .orElse(ReceivingInspectionReviewMode.DIRECT);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceivingInspectionPolicyResponse update(
        UpdateReceivingInspectionPolicyRequest request,
        Long actorId,
        String actorName
    ) {
        save(DEFAULT_REQUIRED_KEY, Boolean.toString(request.defaultInspectionRequired()),
            SystemConfig.ConfigType.BOOLEAN, request.reason(), actorId, actorName);
        save(REVIEW_MODE_KEY, request.reviewMode().name(),
            SystemConfig.ConfigType.STRING, request.reason(), actorId, actorName);
        return getPolicy();
    }

    private void save(
        String key,
        String value,
        SystemConfig.ConfigType type,
        String reason,
        Long actorId,
        String actorName
    ) {
        SystemConfig config = configRepository.findByConfigKeyForUpdate(key)
            .orElseGet(() -> SystemConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        config.setConfigType(type.name());
        config.setDescription(
            "Changed by " + actorId + ":" + actorName + "; " + reason.trim());
        configRepository.save(config);
    }
}

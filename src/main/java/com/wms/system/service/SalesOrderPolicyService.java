package com.wms.system.service;

import com.wms.system.dto.sales.SalesOrderPolicyResponse;
import com.wms.system.dto.sales.UpdateSalesOrderPolicyRequest;
import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.SalesApprovalMode;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SalesOrderPolicyService {
    public static final String APPROVAL_MODE_KEY = "sales.order.approval.mode";
    public static final String APPROVAL_THRESHOLD_KEY = "sales.approval.amount_threshold";
    public static final String DETAILED_LOGISTICS_KEY = "sales.logistics.detailed.enabled";

    private final SystemConfigRepository configRepository;

    @Transactional(readOnly = true)
    public SalesOrderPolicyResponse getPolicy() {
        return new SalesOrderPolicyResponse(
            approvalMode(), approvalAmountThreshold(), detailedLogisticsEnabled(),
            List.of(SalesApprovalMode.DIRECT, SalesApprovalMode.RISK_BASED, SalesApprovalMode.ALWAYS)
        );
    }

    @Transactional(readOnly = true)
    public SalesApprovalMode approvalMode() {
        return configRepository.findByConfigKey(APPROVAL_MODE_KEY)
            .map(SystemConfig::getConfigValue)
            .map(this::parseMode)
            .orElse(SalesApprovalMode.DIRECT);
    }

    @Transactional(readOnly = true)
    public BigDecimal approvalAmountThreshold() {
        String value = configRepository.findByConfigKey(APPROVAL_THRESHOLD_KEY)
            .map(SystemConfig::getConfigValue)
            .orElse("50000.00");
        try {
            BigDecimal threshold = new BigDecimal(value.trim());
            if (threshold.signum() < 0) throw new NumberFormatException("negative");
            return threshold;
        } catch (RuntimeException exception) {
            throw invalid("approvalAmountThreshold", value);
        }
    }

    @Transactional(readOnly = true)
    public boolean detailedLogisticsEnabled() {
        return configRepository.findByConfigKey(DETAILED_LOGISTICS_KEY)
            .map(SystemConfig::getConfigValue)
            .map(String::trim)
            .map(Boolean::parseBoolean)
            .orElse(false);
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderPolicyResponse update(
        UpdateSalesOrderPolicyRequest request,
        Long actorId,
        String actorName
    ) {
        String description = "Changed by " + actorId + ":" + actorName + "; " + request.reason().trim();
        save(APPROVAL_MODE_KEY, request.approvalMode().name(), SystemConfig.ConfigType.STRING, description);
        save(APPROVAL_THRESHOLD_KEY, request.approvalAmountThreshold().toPlainString(),
            SystemConfig.ConfigType.DECIMAL, description);
        save(DETAILED_LOGISTICS_KEY, Boolean.toString(request.detailedLogisticsEnabled()),
            SystemConfig.ConfigType.BOOLEAN, description);
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

    private SalesApprovalMode parseMode(String value) {
        try {
            return SalesApprovalMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalid("approvalMode", value);
        }
    }

    private BusinessException invalid(String field, String value) {
        return new BusinessException(ErrorKeys.VALIDATION_FAILED,
            Map.of("field", field, "configuredValue", value));
    }
}

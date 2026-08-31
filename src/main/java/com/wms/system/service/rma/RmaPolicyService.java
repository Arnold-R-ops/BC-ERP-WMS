package com.wms.system.service.rma;

import com.wms.system.dto.rma.RmaPolicyResponse;
import com.wms.system.dto.rma.UpdateRmaPolicyRequest;
import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.RmaApprovalMode;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RmaPolicyService {
    public static final String APPROVAL_MODE = "sales.rma.approval.mode";
    public static final String STANDARD_DAYS = "sales.rma.standard_return_days";
    public static final String REFUND_ONLY = "sales.rma.refund_only.enabled";
    public static final String ADVANCE = "sales.rma.advance_resolution.enabled";
    public static final String QUARANTINE_STAGING = "sales.rma.quarantine_staging.enabled";

    private final SystemConfigRepository repository;

    @Transactional(readOnly = true)
    public RmaPolicyResponse get() {
        return new RmaPolicyResponse(approvalMode(), integer(STANDARD_DAYS, 5),
            bool(REFUND_ONLY, false), bool(ADVANCE, false), bool(QUARANTINE_STAGING, false));
    }

    @Transactional(readOnly = true)
    public RmaApprovalMode approvalMode() {
        String value = value(APPROVAL_MODE, RmaApprovalMode.DIRECT.name());
        try {
            return RmaApprovalMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalid(APPROVAL_MODE, value);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public RmaPolicyResponse update(UpdateRmaPolicyRequest request, Long actorId, String actorName) {
        String description = "Changed by " + actorId + ":" + actorName + "; " + request.reason().trim();
        save(APPROVAL_MODE, request.approvalMode().name(), SystemConfig.ConfigType.STRING, description);
        save(STANDARD_DAYS, request.standardReturnDays().toString(), SystemConfig.ConfigType.INTEGER, description);
        save(REFUND_ONLY, Boolean.toString(request.refundOnlyEnabled()), SystemConfig.ConfigType.BOOLEAN, description);
        save(ADVANCE, Boolean.toString(request.advanceResolutionEnabled()), SystemConfig.ConfigType.BOOLEAN, description);
        save(QUARANTINE_STAGING, Boolean.toString(request.quarantineStagingEnabled()), SystemConfig.ConfigType.BOOLEAN, description);
        return get();
    }

    private String value(String key, String fallback) {
        return repository.findByConfigKey(key).map(SystemConfig::getConfigValue).orElse(fallback);
    }

    private int integer(String key, int fallback) {
        String value = value(key, Integer.toString(fallback));
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException exception) {
            throw invalid(key, value);
        }
    }

    private boolean bool(String key, boolean fallback) {
        return Boolean.parseBoolean(value(key, Boolean.toString(fallback)).trim());
    }

    private void save(String key, String value, SystemConfig.ConfigType type, String description) {
        SystemConfig config = repository.findByConfigKeyForUpdate(key)
            .orElseGet(() -> SystemConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        config.setConfigType(type.name());
        config.setDescription(description);
        repository.save(config);
    }

    private BusinessException invalid(String field, String value) {
        return new BusinessException(ErrorKeys.VALIDATION_FAILED,
            Map.of("field", field, "configuredValue", value));
    }
}

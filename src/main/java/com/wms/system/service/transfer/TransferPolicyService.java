package com.wms.system.service.transfer;

import com.wms.system.dto.transfer.*;
import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.TransferApprovalMode;
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
public class TransferPolicyService {
    public static final String APPROVAL_MODE = "transfer.approval.mode";
    public static final String RECEIVING_INSPECTION = "transfer.receiving_inspection.enabled";

    private final SystemConfigRepository repository;

    @Transactional(readOnly = true)
    public TransferPolicyResponse get() {
        return new TransferPolicyResponse(approvalMode(), bool(RECEIVING_INSPECTION, false));
    }

    @Transactional(readOnly = true)
    public TransferApprovalMode approvalMode() {
        String configured = value(APPROVAL_MODE, TransferApprovalMode.DIRECT.name());
        try {
            return TransferApprovalMode.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalid(APPROVAL_MODE, configured);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferPolicyResponse update(UpdateTransferPolicyRequest request,
                                         Long actorId, String actorName) {
        String description = "Changed by " + actorId + ":" + actorName + "; " + request.reason().trim();
        save(APPROVAL_MODE, request.approvalMode().name(), SystemConfig.ConfigType.STRING, description);
        save(RECEIVING_INSPECTION, Boolean.toString(request.receivingInspectionEnabled()),
            SystemConfig.ConfigType.BOOLEAN, description);
        return get();
    }

    private String value(String key, String fallback) {
        return repository.findByConfigKey(key).map(SystemConfig::getConfigValue).orElse(fallback);
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

package com.wms.system.service;

import com.wms.system.dto.v45.EmergencyStockCorrectionPolicyResponse;
import com.wms.system.entity.InventoryCorrectionPolicyAudit;
import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.EmergencyCorrectionApprovalMode;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryCorrectionPolicyAuditRepository;
import com.wms.system.repository.SystemConfigRepository;
import com.wms.system.subscription.service.SubscriptionEntitlementService;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryCorrectionPolicyService {
    public static final String CONFIG_KEY = "inventory.correction.approval_mode";
    public static final String APPROVAL_ENTITLEMENT = "INVENTORY_CORRECTION_APPROVAL";

    private final SystemConfigRepository systemConfigRepository;
    private final InventoryCorrectionPolicyAuditRepository auditRepository;
    private final SubscriptionEntitlementService entitlementService;

    @Transactional(readOnly = true)
    public EmergencyStockCorrectionPolicyResponse getPolicy() {
        EmergencyCorrectionApprovalMode configured = readConfiguredMode();
        boolean entitled = entitlementService.hasEntitlement(APPROVAL_ENTITLEMENT);
        return response(configured, entitled);
    }

    @Transactional(readOnly = true)
    public EmergencyCorrectionApprovalMode effectiveMode() {
        EmergencyCorrectionApprovalMode configured = readConfiguredMode();
        return entitlementService.hasEntitlement(APPROVAL_ENTITLEMENT)
            ? configured
            : EmergencyCorrectionApprovalMode.NONE;
    }

    @Transactional(rollbackFor = Exception.class)
    public EmergencyStockCorrectionPolicyResponse updatePolicy(
        EmergencyCorrectionApprovalMode requestedMode,
        String reason,
        Long operatorId,
        String operatorUsername
    ) {
        boolean entitled = entitlementService.hasEntitlement(APPROVAL_ENTITLEMENT);
        if (requestedMode != EmergencyCorrectionApprovalMode.NONE && !entitled) {
            throw new BusinessException(
                ErrorKeys.INVENTORY_CORRECTION_APPROVAL_NOT_ENTITLED,
                Map.of("requestedMode", requestedMode.name())
            );
        }

        SystemConfig config = systemConfigRepository.findByConfigKeyForUpdate(CONFIG_KEY)
            .orElse(null);
        EmergencyCorrectionApprovalMode previous = config == null
            ? EmergencyCorrectionApprovalMode.NONE
            : parse(config.getConfigValue());

        if (previous == requestedMode) {
            return response(requestedMode, entitled);
        }

        if (config == null) {
            config = SystemConfig.builder()
                .configKey(CONFIG_KEY)
                .configValue(requestedMode.name())
                .description("Inventory correction approval workflow mode")
                .configType(SystemConfig.ConfigType.STRING.name())
                .build();
        } else {
            config.setConfigValue(requestedMode.name());
        }
        systemConfigRepository.save(config);

        auditRepository.save(InventoryCorrectionPolicyAudit.builder()
            .companyId(CompanyScope.currentCompanyId())
            .oldMode(previous)
            .newMode(requestedMode)
            .operatorId(operatorId)
            .operatorUsername(operatorUsername)
            .reason(reason.trim())
            .approvalEntitled(entitled)
            .build());

        return response(requestedMode, entitled);
    }

    private EmergencyCorrectionApprovalMode readConfiguredMode() {
        return systemConfigRepository.findByConfigKey(CONFIG_KEY)
            .map(SystemConfig::getConfigValue)
            .map(this::parse)
            .orElse(EmergencyCorrectionApprovalMode.NONE);
    }

    private EmergencyCorrectionApprovalMode parse(String value) {
        try {
            return EmergencyCorrectionApprovalMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new BusinessException(
                ErrorKeys.INVENTORY_CORRECTION_POLICY_INVALID,
                Map.of("configKey", CONFIG_KEY, "configuredValue", value)
            );
        }
    }

    private EmergencyStockCorrectionPolicyResponse response(
        EmergencyCorrectionApprovalMode configured,
        boolean entitled
    ) {
        EmergencyCorrectionApprovalMode effective = entitled
            ? configured
            : EmergencyCorrectionApprovalMode.NONE;
        return EmergencyStockCorrectionPolicyResponse.builder()
            .configuredMode(configured)
            .effectiveMode(effective)
            .approvalEntitled(entitled)
            .configurable(entitled)
            .availableModes(entitled
                ? List.of(
                    EmergencyCorrectionApprovalMode.NONE,
                    EmergencyCorrectionApprovalMode.TWO_PERSON,
                    EmergencyCorrectionApprovalMode.THREE_PERSON)
                : List.of(EmergencyCorrectionApprovalMode.NONE))
            .build();
    }
}

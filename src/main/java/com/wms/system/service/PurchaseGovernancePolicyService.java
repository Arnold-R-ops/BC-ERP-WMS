package com.wms.system.service;

import com.wms.system.dto.purchase.PurchaseGovernancePolicyResponse;
import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.PurchaseApprovalMode;
import com.wms.system.entity.enums.ShortReceiptClosureMode;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.SystemConfigRepository;
import com.wms.system.subscription.service.SubscriptionEntitlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps optional enterprise approval out of the ordinary-tenant happy path.
 * Configuration is tenant scoped by SystemConfig RLS; entitlements cap the
 * effective value without silently destroying the administrator's setting.
 */
@Service
@RequiredArgsConstructor
public class PurchaseGovernancePolicyService {
    public static final String PURCHASE_APPROVAL_KEY = "purchase.approval.mode";
    public static final String SHORT_RECEIPT_CLOSURE_KEY = "purchase.short_receipt_closure.mode";
    public static final String APPROVAL_ENTITLEMENT = "PURCHASE_APPROVAL";

    private final SystemConfigRepository systemConfigRepository;
    private final SubscriptionEntitlementService entitlementService;

    @Transactional(readOnly = true)
    public PurchaseGovernancePolicyResponse getPolicy() {
        PurchaseApprovalMode purchaseMode = configuredPurchaseMode();
        ShortReceiptClosureMode closureMode = configuredClosureMode();
        return response(purchaseMode, closureMode, entitled());
    }

    @Transactional(readOnly = true)
    public PurchaseApprovalMode effectivePurchaseApprovalMode() {
        return entitled() ? configuredPurchaseMode() : PurchaseApprovalMode.NONE;
    }

    @Transactional(readOnly = true)
    public ShortReceiptClosureMode effectiveShortReceiptClosureMode() {
        return entitled()
            ? configuredClosureMode()
            : ShortReceiptClosureMode.DIRECT_WITH_REASON;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseGovernancePolicyResponse updatePolicy(
        PurchaseApprovalMode purchaseMode,
        ShortReceiptClosureMode closureMode,
        String reason,
        Long operatorId,
        String operatorUsername
    ) {
        boolean entitled = entitled();
        if (!entitled && (purchaseMode != PurchaseApprovalMode.NONE
                || closureMode != ShortReceiptClosureMode.DIRECT_WITH_REASON)) {
            throw new BusinessException(
                "PURCHASE_APPROVAL_NOT_ENTITLED",
                Map.of(
                    "purchaseApprovalMode", purchaseMode.name(),
                    "shortReceiptClosureMode", closureMode.name()
                )
            );
        }

        upsert(PURCHASE_APPROVAL_KEY, purchaseMode.name(),
            "Optional purchase-order approval mode");
        upsert(SHORT_RECEIPT_CLOSURE_KEY, closureMode.name(),
            "Governance for closing an unreceived purchase remainder");

        // SystemConfig already captures tenant and timestamps. Keep actor and
        // reason in its human-readable description until a shared policy-audit
        // aggregate is introduced; do not accept either value from a client as
        // the authoritative actor.
        upsert("purchase.policy.last_change", operatorId + ":" + operatorUsername,
            reason.trim());
        return response(purchaseMode, closureMode, entitled);
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

    private PurchaseApprovalMode configuredPurchaseMode() {
        return systemConfigRepository.findByConfigKey(PURCHASE_APPROVAL_KEY)
            .map(SystemConfig::getConfigValue)
            .map(value -> parse(PurchaseApprovalMode.class, value, PURCHASE_APPROVAL_KEY))
            .orElse(PurchaseApprovalMode.NONE);
    }

    private ShortReceiptClosureMode configuredClosureMode() {
        return systemConfigRepository.findByConfigKey(SHORT_RECEIPT_CLOSURE_KEY)
            .map(SystemConfig::getConfigValue)
            .map(value -> parse(ShortReceiptClosureMode.class, value, SHORT_RECEIPT_CLOSURE_KEY))
            .orElse(ShortReceiptClosureMode.DIRECT_WITH_REASON);
    }

    private boolean entitled() {
        return entitlementService.hasEntitlement(APPROVAL_ENTITLEMENT);
    }

    private <T extends Enum<T>> T parse(Class<T> type, String value, String key) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new BusinessException(
                "PURCHASE_POLICY_INVALID",
                Map.of("configKey", key, "configuredValue", value)
            );
        }
    }

    private PurchaseGovernancePolicyResponse response(
        PurchaseApprovalMode configuredPurchase,
        ShortReceiptClosureMode configuredClosure,
        boolean approvalEntitled
    ) {
        return PurchaseGovernancePolicyResponse.builder()
            .configuredPurchaseApprovalMode(configuredPurchase)
            .effectivePurchaseApprovalMode(approvalEntitled
                ? configuredPurchase : PurchaseApprovalMode.NONE)
            .configuredShortReceiptClosureMode(configuredClosure)
            .effectiveShortReceiptClosureMode(approvalEntitled
                ? configuredClosure : ShortReceiptClosureMode.DIRECT_WITH_REASON)
            .approvalEntitled(approvalEntitled)
            .availablePurchaseApprovalModes(approvalEntitled
                ? List.of(PurchaseApprovalMode.NONE, PurchaseApprovalMode.SIMPLE)
                : List.of(PurchaseApprovalMode.NONE))
            .availableShortReceiptClosureModes(approvalEntitled
                ? List.of(ShortReceiptClosureMode.DIRECT_WITH_REASON,
                    ShortReceiptClosureMode.APPROVAL_REQUIRED)
                : List.of(ShortReceiptClosureMode.DIRECT_WITH_REASON))
            .build();
    }
}

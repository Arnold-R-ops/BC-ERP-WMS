package com.wms.system.service;

import com.wms.system.entity.ProductSku;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Validates production and expiry dates at the point inventory is confirmed.
 *
 * <p>An expiry-managed SKU requires both dates. An unmanaged SKU may omit
 * either or both dates, but any supplied dates must still be internally
 * reasonable so invalid inventory metadata cannot be persisted.</p>
 */
@Component
public class ProductSkuExpiryPolicy {

    private final Clock clock;

    public ProductSkuExpiryPolicy() {
        this(Clock.systemDefaultZone());
    }

    ProductSkuExpiryPolicy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void validateForInventoryReceipt(
        ProductSku productSku,
        LocalDate productionDate,
        LocalDate expiryDate
    ) {
        Objects.requireNonNull(productSku, "productSku");

        if (Boolean.TRUE.equals(productSku.getExpiryManagementEnabled())) {
            if (productionDate == null) {
                throw validationError(
                    ErrorKeys.PRODUCT_SKU_PRODUCTION_DATE_REQUIRED,
                    productSku,
                    "PRODUCTION_DATE_REQUIRED"
                );
            }
            if (expiryDate == null) {
                throw validationError(
                    ErrorKeys.PRODUCT_SKU_EXPIRY_DATE_REQUIRED,
                    productSku,
                    "EXPIRY_DATE_REQUIRED"
                );
            }
        }

        validateSuppliedDates(productSku, productionDate, expiryDate);
    }

    public void validateSuppliedDates(ProductSku productSku, LocalDate productionDate, LocalDate expiryDate) {
        Objects.requireNonNull(productSku, "productSku");
        LocalDate businessDate = LocalDate.now(clock);
        if (productionDate != null
                && expiryDate != null
                && expiryDate.isBefore(productionDate)) {
            throw validationError(
                ErrorKeys.PRODUCT_SKU_EXPIRY_DATE_INVALID,
                productSku,
                "EXPIRY_BEFORE_PRODUCTION"
            );
        }
        if (productionDate != null && productionDate.isAfter(businessDate)) {
            throw validationError(
                ErrorKeys.PRODUCT_SKU_PRODUCTION_DATE_INVALID,
                productSku,
                "FUTURE_PRODUCTION_DATE"
            );
        }
        if (expiryDate != null && expiryDate.isBefore(businessDate)) {
            throw validationError(
                ErrorKeys.PRODUCT_SKU_EXPIRY_DATE_INVALID,
                productSku,
                "EXPIRED_ON_RECEIPT"
            );
        }
    }

    private BusinessException validationError(
        String errorKey,
        ProductSku productSku,
        String reason
    ) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (productSku.getId() != null) {
            params.put("productSkuId", productSku.getId());
        }
        if (productSku.getSkuCode() != null) {
            params.put("skuCode", productSku.getSkuCode());
        }
        params.put("reason", reason);
        return new BusinessException(errorKey, params);
    }
}

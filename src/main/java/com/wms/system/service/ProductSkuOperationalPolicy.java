package com.wms.system.service;

import com.wms.system.entity.ProductSku;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enforces whether an SKU may enter a new operational flow.
 *
 * Existing receipts and outbound tasks are intentionally allowed to finish;
 * callers use this policy only when creating or allocating new work.
 */
@Component
public class ProductSkuOperationalPolicy {

    public static final String SALES_ORDER = "SALES_ORDER";
    public static final String PURCHASE_ORDER = "PURCHASE_ORDER";
    public static final String INBOUND_ORDER = "INBOUND_ORDER";
    public static final String INVENTORY_ALLOCATION = "INVENTORY_ALLOCATION";

    public void requireEnabled(ProductSku productSku, String operation) {
        if (!Boolean.TRUE.equals(productSku.getEnabled())) {
            throw new BusinessException(
                ErrorKeys.PRODUCT_SKU_DISABLED,
                Map.of(
                    "productSkuId", productSku.getId(),
                    "skuCode", productSku.getSkuCode(),
                    "operation", operation
                )
            );
        }

        if (productSku.getProduct() != null
                && !Boolean.TRUE.equals(productSku.getProduct().getEnabled())) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("productId", productSku.getProduct().getId());
            params.put("productCode", productSku.getProduct().getProductCode());
            params.put("productSkuId", productSku.getId());
            params.put("skuCode", productSku.getSkuCode());
            params.put("operation", operation);
            throw new BusinessException(ErrorKeys.PRODUCT_DISABLED, params);
        }
    }
}

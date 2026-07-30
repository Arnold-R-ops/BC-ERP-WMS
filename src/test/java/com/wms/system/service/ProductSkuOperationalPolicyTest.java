package com.wms.system.service;

import com.wms.system.entity.Product;
import com.wms.system.entity.ProductSku;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSkuOperationalPolicyTest {

    private final ProductSkuOperationalPolicy policy = new ProductSkuOperationalPolicy();

    @Test
    void allowsEnabledSku() {
        Product product = Product.builder()
            .id(10L)
            .productCode("PROD-10")
            .enabled(true)
            .build();
        ProductSku productSku = ProductSku.builder()
            .id(1L)
            .skuCode("SKU00000001")
            .enabled(true)
            .product(product)
            .build();

        assertThatCode(() ->
            policy.requireEnabled(productSku, ProductSkuOperationalPolicy.SALES_ORDER)
        ).doesNotThrowAnyException();
    }

    @Test
    void rejectsDisabledSkuWithOperationContext() {
        ProductSku productSku = ProductSku.builder()
            .id(2L)
            .skuCode("SKU00000002")
            .enabled(false)
            .build();

        assertThatThrownBy(() ->
            policy.requireEnabled(productSku, ProductSkuOperationalPolicy.PURCHASE_ORDER)
        )
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.PRODUCT_SKU_DISABLED)
            .satisfies(error -> {
                BusinessException businessException = (BusinessException) error;
                org.assertj.core.api.Assertions.assertThat(businessException.getParams())
                    .containsEntry("productSkuId", 2L)
                    .containsEntry("skuCode", "SKU00000002")
                    .containsEntry("operation", ProductSkuOperationalPolicy.PURCHASE_ORDER);
            });
    }

    @Test
    void rejectsSkuWhoseParentProductIsDisabled() {
        Product product = Product.builder()
            .id(20L)
            .productCode("PROD-20")
            .enabled(false)
            .build();
        ProductSku productSku = ProductSku.builder()
            .id(3L)
            .skuCode("SKU00000003")
            .enabled(true)
            .product(product)
            .build();

        assertThatThrownBy(() ->
            policy.requireEnabled(productSku, ProductSkuOperationalPolicy.INBOUND_ORDER)
        )
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.PRODUCT_DISABLED)
            .satisfies(error -> {
                BusinessException businessException = (BusinessException) error;
                org.assertj.core.api.Assertions.assertThat(businessException.getParams())
                    .containsEntry("productId", 20L)
                    .containsEntry("productSkuId", 3L)
                    .containsEntry("operation", ProductSkuOperationalPolicy.INBOUND_ORDER);
            });
    }
}

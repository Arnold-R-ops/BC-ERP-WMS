package com.wms.system.service;

import com.wms.system.entity.ProductSku;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSkuExpiryPolicyTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 28);

    private final ProductSkuExpiryPolicy policy = new ProductSkuExpiryPolicy(
        Clock.fixed(Instant.parse("2026-08-28T08:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void shippingAdviceMayOmitDatesForExpiryManagedSkuButReceiptMayNot() {
        assertThatCode(() -> policy.validateSuppliedDates(sku(true), null, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validateForInventoryReceipt(sku(true), null, null))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy.validateSuppliedDates(sku(true), BUSINESS_DATE.plusDays(1), null))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void unmanagedSkuAllowsBothDatesToBeAbsent() {
        ProductSku sku = sku(false);

        assertThatCode(() ->
            policy.validateForInventoryReceipt(sku, null, null)
        ).doesNotThrowAnyException();
    }

    @Test
    void managedSkuRequiresProductionDate() {
        ProductSku sku = sku(true);

        assertThatThrownBy(() ->
            policy.validateForInventoryReceipt(sku, null, BUSINESS_DATE.plusMonths(6))
        )
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue(
                "errorKey",
                ErrorKeys.PRODUCT_SKU_PRODUCTION_DATE_REQUIRED
            );
    }

    @Test
    void managedSkuRequiresExpiryDate() {
        ProductSku sku = sku(true);

        assertThatThrownBy(() ->
            policy.validateForInventoryReceipt(sku, BUSINESS_DATE.minusDays(1), null)
        )
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue(
                "errorKey",
                ErrorKeys.PRODUCT_SKU_EXPIRY_DATE_REQUIRED
            );
    }

    @Test
    void managedSkuAcceptsReasonableDates() {
        ProductSku sku = sku(true);

        assertThatCode(() ->
            policy.validateForInventoryReceipt(
                sku,
                BUSINESS_DATE.minusDays(10),
                BUSINESS_DATE.plusMonths(6)
            )
        ).doesNotThrowAnyException();
    }

    @Test
    void anySuppliedProductionDateCannotBeInTheFuture() {
        ProductSku sku = sku(false);

        assertThatThrownBy(() ->
            policy.validateForInventoryReceipt(
                sku,
                BUSINESS_DATE.plusDays(1),
                BUSINESS_DATE.plusMonths(6)
            )
        )
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue(
                "errorKey",
                ErrorKeys.PRODUCT_SKU_PRODUCTION_DATE_INVALID
            );
    }

    @Test
    void anySuppliedExpiryDateCannotAlreadyBeExpired() {
        ProductSku sku = sku(false);

        assertThatThrownBy(() ->
            policy.validateForInventoryReceipt(
                sku,
                BUSINESS_DATE.minusMonths(6),
                BUSINESS_DATE.minusDays(1)
            )
        )
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue(
                "errorKey",
                ErrorKeys.PRODUCT_SKU_EXPIRY_DATE_INVALID
            )
            .satisfies(error ->
                org.assertj.core.api.Assertions.assertThat(
                    ((BusinessException) error).getParam("reason")
                ).isEqualTo("EXPIRED_ON_RECEIPT")
            );
    }

    @Test
    void expiryDateCannotPrecedeProductionDate() {
        ProductSku sku = sku(false);

        assertThatThrownBy(() ->
            policy.validateForInventoryReceipt(
                sku,
                BUSINESS_DATE.minusDays(1),
                BUSINESS_DATE.minusDays(2)
            )
        )
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue(
                "errorKey",
                ErrorKeys.PRODUCT_SKU_EXPIRY_DATE_INVALID
            )
            .satisfies(error ->
                org.assertj.core.api.Assertions.assertThat(
                    ((BusinessException) error).getParam("reason")
                ).isEqualTo("EXPIRY_BEFORE_PRODUCTION")
            );
    }

    private ProductSku sku(boolean expiryManagementEnabled) {
        return ProductSku.builder()
            .id(41L)
            .skuCode("SKU00000041")
            .expiryManagementEnabled(expiryManagementEnabled)
            .build();
    }
}

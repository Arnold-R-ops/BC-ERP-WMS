package com.wms.system.service;

import com.wms.system.dto.customer.CreateCustomerRequest;
import com.wms.system.entity.ProductSku;
import jakarta.validation.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AddressAndBaseUnitValidationTest {
    @Test void addressesAccept255CharactersAndReject256ForBothFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = CreateCustomerRequest.builder().name("客户").shippingSameAsBilling(false)
                .address("中".repeat(255)).shippingAddress("文".repeat(255)).build();
            assertThat(validator.validate(request)).isEmpty();
            request.setAddress("中".repeat(256)); request.setShippingAddress("文".repeat(256));
            assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .contains("address", "shippingAddress");
        }
    }
    @Test void differentShippingAddressCannotBeEmpty() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = CreateCustomerRequest.builder().name("客户").shippingSameAsBilling(false).build();
            assertThat(factory.getValidator().validate(request)).extracting(v -> v.getPropertyPath().toString()).contains("shippingAddressComplete");
            request.setShippingSameAsBilling(true);
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }
    @Test void packageRemainderUsesConfiguredBaseUnit() {
        var sku = ProductSku.builder().conversionRate(12).packUnit("箱").baseUnit("袋").build();
        assertThat(sku.formatQuantity(29)).contains("2", "箱", "5", "袋").doesNotContain("件", "Unit");
        assertThat(sku.formatQuantity(5)).contains("5", "袋");
        sku.setBaseUnit(null);
        assertThat(sku.formatQuantity(5)).contains("件");
    }
}

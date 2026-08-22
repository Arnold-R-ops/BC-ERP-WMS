package com.wms.system.platform.service;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class PlatformOperationCatalogTest {
    @Test void allowsOnlyFixedProductAndCustomerFields() {
        assertThatCode(() -> PlatformOperationCatalog.validate("WRITE", "product", Map.of("brand", "A")))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> PlatformOperationCatalog.validate("WRITE", "product", Map.of("password", "x")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlatformOperationCatalog.validate("DELETE", "users", Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlatformOperationCatalog.validate("DELETE", "product", Map.of("anything", "x")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

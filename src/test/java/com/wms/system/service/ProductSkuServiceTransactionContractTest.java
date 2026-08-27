package com.wms.system.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSkuServiceTransactionContractTest {

    @Test
    void tenantScopedReadMethodsOpenReadOnlyTransactionsForPostgresRls() throws Exception {
        assertReadOnlyTransaction(ProductSkuService.class.getDeclaredMethod("getProductSku", Long.class));
        assertReadOnlyTransaction(ProductSkuService.class.getDeclaredMethod(
            "listProductSkus", Boolean.class, Long.class));
    }

    private static void assertReadOnlyTransaction(Method method) {
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
            method, Transactional.class);

        assertThat(transactional)
            .as("%s must bind app.company_id through CompanyRlsJpaTransactionManager", method.getName())
            .isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}

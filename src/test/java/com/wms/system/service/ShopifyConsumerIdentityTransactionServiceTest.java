package com.wms.system.service;

import com.wms.system.dto.shopify.ShopifyOrderDto;
import com.wms.system.entity.IntegrationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ShopifyConsumerIdentityTransactionServiceTest {

    @Test
    void identityWorkerUsesRequiresNew() throws Exception {
        Method method = ShopifyConsumerIdentityTransactionService.class.getMethod(
            "resolveAndCommit",
            ShopifyOrderDto.class,
            IntegrationConfig.class
        );

        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}

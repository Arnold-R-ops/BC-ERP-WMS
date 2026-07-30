package com.wms.system.integration;

import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.shopify.ShopifyCustomerDto;
import com.wms.system.dto.shopify.ShopifyOrderDto;
import com.wms.system.entity.Customer;
import com.wms.system.entity.IntegrationConfig;
import com.wms.system.repository.CustomerRepository;
import com.wms.system.service.ShopifyConsumerIdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class ShopifyConsumerIdentityTransactionIntegrationTest {

    @Autowired
    private ShopifyConsumerIdentityService identityService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void consumerSurvivesRollbackOfTheCallingOrderTransaction() {
        String suffix = UUID.randomUUID().toString();
        String email = "rollback-" + suffix + "@example.com";
        ShopifyOrderDto order = order(Math.abs(suffix.hashCode()) + 10_000L, email);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            identityService.resolveForAutomaticIngestion(order, retailConfig(true));
            throw new IllegalStateException("simulate unknown SKU");
        })).isInstanceOf(IllegalStateException.class);

        Customer consumer = customerRepository
            .findByCompanyIdAndNormalizedEmailOrderByIdAsc(1L, email)
            .get(0);
        assertThat(consumer.getExternalCustomerId()).isNotBlank();
        customerRepository.delete(consumer);
    }

    @Test
    void concurrentDeliveriesReuseOneConsumer() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String email = "concurrent-" + suffix + "@example.com";
        ShopifyOrderDto order = order(Math.abs(suffix.hashCode()) + 20_000L, email);
        IntegrationConfig config = retailConfig(true);

        var executor = Executors.newFixedThreadPool(6);
        try {
            Callable<Long> resolve = () ->
                identityService.resolveForAutomaticIngestion(order, config).getId();
            List<Callable<Long>> calls = List.of(resolve, resolve, resolve, resolve, resolve, resolve);
            List<Long> ids = executor.invokeAll(calls).stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toList();

            assertThat(ids).containsOnly(ids.get(0));
            List<Customer> matches =
                customerRepository.findByCompanyIdAndNormalizedEmailOrderByIdAsc(1L, email);
            assertThat(matches).hasSize(1);
            customerRepository.delete(matches.get(0));
        } finally {
            executor.shutdownNow();
        }
    }

    private ShopifyOrderDto order(Long externalCustomerId, String email) {
        ShopifyCustomerDto customer = new ShopifyCustomerDto();
        customer.setId(externalCustomerId);
        customer.setEmail(email);
        customer.setFirstName("Shopify");
        customer.setLastName("Buyer");

        ShopifyOrderDto order = new ShopifyOrderDto();
        order.setName("#TX-" + externalCustomerId);
        order.setEmail(email);
        order.setCustomer(customer);
        return order;
    }

    private IntegrationConfig retailConfig(boolean enabled) {
        return IntegrationConfig.builder()
            .id(1L)
            .storeUrl("transaction-boundary-test.myshopify.com")
            .retailMode(enabled)
            .build();
    }
}

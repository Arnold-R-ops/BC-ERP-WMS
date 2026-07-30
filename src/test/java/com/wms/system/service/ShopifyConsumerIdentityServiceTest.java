package com.wms.system.service;

import com.wms.system.dto.shopify.ShopifyCustomerDto;
import com.wms.system.dto.shopify.ShopifyOrderDto;
import com.wms.system.entity.Customer;
import com.wms.system.entity.IntegrationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopifyConsumerIdentityServiceTest {

    @Mock
    private ShopifyConsumerIdentityTransactionService identityTransactionService;

    @InjectMocks
    private ShopifyConsumerIdentityService service;

    @Test
    void delegatesToTheDedicatedIdentityTransactionWorker() {
        ShopifyOrderDto order = order(9001L, "buyer@example.com");
        IntegrationConfig config = IntegrationConfig.builder().retailMode(true).build();
        Customer expected = Customer.builder().id(71L).build();
        when(identityTransactionService.resolveAndCommit(order, config)).thenReturn(expected);

        Customer actual = service.resolveForAutomaticIngestion(order, config);

        assertThat(actual).isSameAs(expected);
        verify(identityTransactionService).resolveAndCommit(order, config);
    }

    private ShopifyOrderDto order(Long externalCustomerId, String email) {
        ShopifyCustomerDto customer = new ShopifyCustomerDto();
        customer.setId(externalCustomerId);
        customer.setEmail(email);
        ShopifyOrderDto order = new ShopifyOrderDto();
        order.setCustomer(customer);
        order.setEmail(email);
        return order;
    }
}

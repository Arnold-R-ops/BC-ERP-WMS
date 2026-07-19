package com.wms.system.service;

import com.wms.system.dto.shopify.ShopifyCustomerDto;
import com.wms.system.dto.shopify.ShopifyOrderDto;
import com.wms.system.entity.Customer;
import com.wms.system.entity.IntegrationConfig;
import com.wms.system.entity.enums.CustomerSource;
import com.wms.system.entity.enums.CustomerType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopifyCustomerResolverTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private ShopifyCustomerResolver resolver;

    @Test
    void retailOffReusesUniqueExistingB2bCustomer() {
        ShopifyOrderDto order = order(100L, " Buyer@Example.com ");
        Customer b2b = customer(11L, CustomerType.B2B, "buyer@example.com");
        when(customerRepository.findByCompanyIdAndCustomerTypeAndExternalCustomerId(
            1L, CustomerType.CONSUMER, "100"
        )).thenReturn(Optional.empty());
        when(customerRepository.findByCompanyIdAndNormalizedEmailOrderByIdAsc(
            1L, "buyer@example.com"
        )).thenReturn(List.of(b2b));

        Customer resolved = resolver.resolveForAutomatic(order, config(false));

        assertThat(resolved).isSameAs(b2b);
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void retailOffRejectsUnknownCustomer() {
        ShopifyOrderDto order = order(100L, "buyer@example.com");
        when(customerRepository.findByCompanyIdAndCustomerTypeAndExternalCustomerId(
            1L, CustomerType.CONSUMER, "100"
        )).thenReturn(Optional.empty());
        when(customerRepository.findByCompanyIdAndNormalizedEmailOrderByIdAsc(
            1L, "buyer@example.com"
        )).thenReturn(List.of());

        assertThatThrownBy(() -> resolver.resolveForAutomatic(order, config(false)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.VALIDATION_FAILED);

        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void retailOnReusesExternalIdAndRefreshesConsumerEmail() {
        ShopifyOrderDto order = order(100L, "New@Example.com");
        Customer consumer = customer(12L, CustomerType.CONSUMER, "old@example.com");
        consumer.setExternalCustomerId("100");
        when(customerRepository.findByCompanyIdAndCustomerTypeAndExternalCustomerId(
            1L, CustomerType.CONSUMER, "100"
        )).thenReturn(Optional.of(consumer));

        Customer resolved = resolver.resolveForAutomatic(order, config(true));

        assertThat(resolved).isSameAs(consumer);
        assertThat(consumer.getEmail()).isEqualTo("New@Example.com");
        assertThat(consumer.getNormalizedEmail()).isEqualTo("new@example.com");
        verify(customerRepository).save(consumer);
    }

    @Test
    void retailOnCreatesLightweightConsumerForNewIdentity() {
        ShopifyOrderDto order = order(100L, "new@example.com");
        when(customerRepository.findByCompanyIdAndCustomerTypeAndExternalCustomerId(
            1L, CustomerType.CONSUMER, "100"
        )).thenReturn(Optional.empty());
        when(customerRepository.findByCompanyIdAndNormalizedEmailOrderByIdAsc(
            1L, "new@example.com"
        )).thenReturn(List.of());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        Customer resolved = resolver.resolveForAutomatic(order, config(true));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        Customer created = captor.getValue();
        assertThat(resolved.getId()).isEqualTo(99L);
        assertThat(created.getCustomerType()).isEqualTo(CustomerType.CONSUMER);
        assertThat(created.getSource()).isEqualTo(CustomerSource.CHANNEL);
        assertThat(created.getExternalCustomerId()).isEqualTo("100");
        assertThat(created.getNormalizedEmail()).isEqualTo("new@example.com");
        assertThat(created.getCreditLimit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(created.getCode()).startsWith("C-SHOPIFY-");
    }

    @Test
    void guestCheckoutReusesUniqueEmailMatch() {
        ShopifyOrderDto order = order(null, "guest@example.com");
        Customer consumer = customer(13L, CustomerType.CONSUMER, "guest@example.com");
        when(customerRepository.findByCompanyIdAndNormalizedEmailOrderByIdAsc(
            1L, "guest@example.com"
        )).thenReturn(List.of(consumer));

        Customer resolved = resolver.resolveForAutomatic(order, config(true));

        assertThat(resolved).isSameAs(consumer);
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void ambiguousB2bEmailIsBlockedWithoutGuessingOrCreation() {
        ShopifyOrderDto order = order(null, "shared@example.com");
        Customer first = customer(21L, CustomerType.B2B, "shared@example.com");
        Customer second = customer(22L, CustomerType.B2B, "shared@example.com");
        when(customerRepository.findByCompanyIdAndNormalizedEmailOrderByIdAsc(
            1L, "shared@example.com"
        )).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> resolver.resolveForAutomatic(order, config(true)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.VALIDATION_FAILED)
            .satisfies(error -> assertThat(((BusinessException) error).getParam("matchCount")).isEqualTo(2));

        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void missingCustomerIdAndEmailIsRejected() {
        ShopifyOrderDto order = order(null, null);

        assertThatThrownBy(() -> resolver.resolveForAutomatic(order, config(true)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.VALIDATION_FAILED);

        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void repairNeverCreatesUnknownCustomerEvenWhenRetailModeWouldNormallyAllowIt() {
        ShopifyOrderDto order = order(100L, "unknown@example.com");
        when(customerRepository.findByCompanyIdAndCustomerTypeAndExternalCustomerId(
            1L, CustomerType.CONSUMER, "100"
        )).thenReturn(Optional.empty());
        when(customerRepository.findByCompanyIdAndNormalizedEmailOrderByIdAsc(
            1L, "unknown@example.com"
        )).thenReturn(List.of());

        assertThatThrownBy(() -> resolver.resolveExistingOnly(order))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.VALIDATION_FAILED);

        verify(customerRepository, never()).save(any(Customer.class));
    }

    private IntegrationConfig config(boolean retailMode) {
        return IntegrationConfig.builder()
            .id(1L)
            .storeUrl("test-store.myshopify.com")
            .retailMode(retailMode)
            .build();
    }

    private ShopifyOrderDto order(Long externalCustomerId, String email) {
        ShopifyOrderDto order = new ShopifyOrderDto();
        order.setName("#1001");
        order.setEmail(email);
        if (externalCustomerId != null) {
            ShopifyCustomerDto customer = new ShopifyCustomerDto();
            customer.setId(externalCustomerId);
            customer.setEmail(email);
            customer.setFirstName("Jane");
            customer.setLastName("Doe");
            order.setCustomer(customer);
        }
        return order;
    }

    private Customer customer(Long id, CustomerType type, String email) {
        return Customer.builder()
            .id(id)
            .code("C-" + id)
            .name("Customer " + id)
            .email(email)
            .normalizedEmail(Customer.normalizeEmail(email))
            .customerType(type)
            .source(type == CustomerType.B2B ? CustomerSource.MANUAL : CustomerSource.CHANNEL)
            .isActive(true)
            .build();
    }
}

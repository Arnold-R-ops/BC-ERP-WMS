package com.wms.system.service;

import com.wms.system.dto.customer.CreateCustomerRequest;
import com.wms.system.dto.customer.CustomerResponse;
import com.wms.system.entity.Customer;
import com.wms.system.entity.User;
import com.wms.system.entity.enums.CustomerSource;
import com.wms.system.entity.enums.CustomerType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.CustomerRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.security.SecurityUser;
import com.wms.system.util.MaskingUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService Tests")
class CustomerServiceTest {

    private static final String CUSTOMER_NAME = "Test Customer";
    private static final String CUSTOMER_CONTACT = "Test Contact";
    private static final String CUSTOMER_ADDRESS = "Shanghai Test Road 88";
    private static final String UPDATED_ADDRESS = "Beijing Updated Road 100";

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private CustomerService customerService;

    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
            .id(1L)
            .code("CUST001")
            .name(CUSTOMER_NAME)
            .contact(CUSTOMER_CONTACT)
            .phone("13912345678")
            .email("zhangsan@example.com")
            .address(CUSTOMER_ADDRESS)
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .ownerId(1L)
            .build();
        testCustomer.setCreatedAt(LocalDateTime.now());
        testCustomer.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("createCustomer success")
    void testCreateCustomerSuccessAutoSetOwner() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name(CUSTOMER_NAME)
            .contact(CUSTOMER_CONTACT)
            .phone("13912345678")
            .email("zhangsan@example.com")
            .address(CUSTOMER_ADDRESS)
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .build();

        mockSecurityContext(1L, List.of(new SimpleGrantedAuthority("SALESPERSON")));
        when(customerRepository.existsByCode("CUST001")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

        CustomerResponse response = customerService.createCustomer(request);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("CUST001");
        assertThat(response.getName()).isEqualTo(CUSTOMER_NAME);
        verify(customerRepository).save(argThat(customer ->
            customer.getOwnerId() != null
                && customer.getOwnerId().equals(1L)
                && customer.getCustomerType() == CustomerType.CLIENT
        ));
    }

    @Test
    @DisplayName("createCustomer duplicate code")
    void testCreateCustomerFailureCodeExists() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name(CUSTOMER_NAME)
            .build();

        when(customerRepository.existsByCode("CUST001")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request))
            .isInstanceOf(BusinessException.class);
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("listCustomers sales only own and masked")
    void testListCustomersSalesOnlyOwnCustomers() {
        mockSecurityContext(1L, List.of(new SimpleGrantedAuthority("SALESPERSON")));
        when(customerRepository.findByOwnerId(1L)).thenReturn(List.of(testCustomer));

        List<CustomerResponse> responses = customerService.listCustomers();

        assertThat(responses).hasSize(1);
        verify(customerRepository).findByOwnerId(1L);
        verify(customerRepository, never()).findAll();
        CustomerResponse response = responses.get(0);
        assertThat(response.getName()).isEqualTo(MaskingUtils.maskName(testCustomer.getName()));
        assertThat(response.getPhone()).isEqualTo("139****5678");
        assertThat(response.getEmail()).isEqualTo("z***@example.com");
        assertThat(response.getAddress()).isEqualTo(MaskingUtils.maskAddress(testCustomer.getAddress()));
    }

    @Test
    @DisplayName("listCustomers admin all unmasked")
    void testListCustomersAdminAllCustomers() {
        mockSecurityContext(1L, List.of(new SimpleGrantedAuthority("SUPER_ADMIN")));
        when(customerRepository.findAll()).thenReturn(List.of(testCustomer));

        List<CustomerResponse> responses = customerService.listCustomers();

        assertThat(responses).hasSize(1);
        verify(customerRepository).findAll();
        verify(customerRepository, never()).findByOwnerId(anyLong());
        CustomerResponse response = responses.get(0);
        assertThat(response.getName()).isEqualTo(CUSTOMER_NAME);
        assertThat(response.getAddress()).isEqualTo(CUSTOMER_ADDRESS);
    }

    @Test
    @DisplayName("listCustomers admin filters CLIENT in repository")
    void testListCustomersAdminFiltersClient() {
        mockSecurityContext(1L, List.of(new SimpleGrantedAuthority("SUPER_ADMIN")));
        when(customerRepository.findByCustomerType(CustomerType.CLIENT)).thenReturn(List.of(testCustomer));

        List<CustomerResponse> responses = customerService.listCustomers(CustomerType.CLIENT);

        assertThat(responses).hasSize(1);
        verify(customerRepository).findByCustomerType(CustomerType.CLIENT);
        verify(customerRepository, never()).findAll();
    }

    @Test
    @DisplayName("listActiveCustomers sales only own active")
    void testListActiveCustomersSalesOnlyOwnActiveCustomers() {
        mockSecurityContext(1L, List.of(new SimpleGrantedAuthority("SALESPERSON")));
        when(customerRepository.findByOwnerIdAndIsActiveTrue(1L)).thenReturn(List.of(testCustomer));

        List<CustomerResponse> responses = customerService.listActiveCustomers();

        assertThat(responses).hasSize(1);
        verify(customerRepository).findByOwnerIdAndIsActiveTrue(1L);
        verify(customerRepository, never()).findByIsActiveTrue();
        assertThat(responses.get(0).getName()).isEqualTo(MaskingUtils.maskName(testCustomer.getName()));
    }

    @Test
    @DisplayName("listActiveCustomers admin all active")
    void testListActiveCustomersAdminAllActiveCustomers() {
        mockSecurityContext(1L, List.of(new SimpleGrantedAuthority("WAREHOUSE_ADMIN")));
        when(customerRepository.findByIsActiveTrue()).thenReturn(List.of(testCustomer));

        List<CustomerResponse> responses = customerService.listActiveCustomers();

        assertThat(responses).hasSize(1);
        verify(customerRepository).findByIsActiveTrue();
        assertThat(responses.get(0).getName()).isEqualTo(CUSTOMER_NAME);
    }

    @Test
    @DisplayName("listActiveCustomers sales filters CLIENT with row isolation")
    void testListActiveCustomersSalesFiltersClient() {
        mockSecurityContext(1L, List.of(new SimpleGrantedAuthority("SALESPERSON")));
        when(customerRepository.findByOwnerIdAndCustomerTypeAndIsActiveTrue(1L, CustomerType.CLIENT))
            .thenReturn(List.of(testCustomer));

        List<CustomerResponse> responses = customerService.listActiveCustomers(CustomerType.CLIENT);

        assertThat(responses).hasSize(1);
        verify(customerRepository)
            .findByOwnerIdAndCustomerTypeAndIsActiveTrue(1L, CustomerType.CLIENT);
        verify(customerRepository, never()).findByIsActiveTrue();
    }

    @Test
    @DisplayName("getCustomer sales masked")
    void testGetCustomerSalesMaskedData() {
        mockSecurityContext(1L, List.of(new SimpleGrantedAuthority("SALESPERSON")));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        CustomerResponse response = customerService.getCustomer(1L);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo(MaskingUtils.maskName(testCustomer.getName()));
        assertThat(response.getAddress()).isEqualTo(MaskingUtils.maskAddress(testCustomer.getAddress()));
    }

    @Test
    @DisplayName("getCustomer admin original")
    void testGetCustomerAdminOriginalData() {
        mockSecurityContext(1L, List.of(new SimpleGrantedAuthority("SUPER_ADMIN")));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        CustomerResponse response = customerService.getCustomer(1L);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo(CUSTOMER_NAME);
        assertThat(response.getPhone()).isEqualTo("13912345678");
        assertThat(response.getEmail()).isEqualTo("zhangsan@example.com");
        assertThat(response.getAddress()).isEqualTo(CUSTOMER_ADDRESS);
    }

    @Test
    @DisplayName("getCustomer not found")
    void testGetCustomerFailureNotFound() {
        when(customerRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomer(1L))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("updateCustomer normal update")
    void testUpdateCustomerSuccessNormalUpdate() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("updated_name")
            .contact("updated_contact")
            .phone("13900000000")
            .email("new@example.com")
            .address(UPDATED_ADDRESS)
            .creditLimit(new BigDecimal("200000.00"))
            .isActive(true)
            .build();

        mockSecurityContext(1L, List.of(new SimpleGrantedAuthority("SUPER_ADMIN")));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

        CustomerResponse response = customerService.updateCustomer(1L, request);

        assertThat(response).isNotNull();
        verify(customerRepository).save(argThat(customer ->
            customer.getPhone().equals("13900000000") &&
            customer.getEmail().equals("new@example.com") &&
            customer.getAddress().equals(UPDATED_ADDRESS)
        ));
    }

    @Test
    @DisplayName("updateCustomer ignores masked fields")
    void testUpdateCustomerEditProtectionIgnoreMaskedFields() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("updated_name")
            .contact("updated_contact")
            .phone("139****5678")
            .email("z***@example.com")
            .address(MaskingUtils.maskAddress(testCustomer.getAddress()))
            .creditLimit(new BigDecimal("200000.00"))
            .isActive(true)
            .build();

        mockSecurityContext(1L, List.of(new SimpleGrantedAuthority("SALESPERSON")));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

        CustomerResponse response = customerService.updateCustomer(1L, request);

        assertThat(response).isNotNull();
        verify(customerRepository).save(argThat(customer ->
            customer.getPhone().equals("13912345678") &&
            customer.getEmail().equals("zhangsan@example.com") &&
            customer.getAddress().equals(CUSTOMER_ADDRESS)
        ));
    }

    @Test
    @DisplayName("updateCustomer rejects channel-managed consumer")
    void testUpdateCustomerRejectsConsumer() {
        Customer consumer = Customer.builder()
            .id(2L)
            .code("CONSUMER001")
            .name("Channel Consumer")
            .customerType(CustomerType.CONSUMER)
            .source(CustomerSource.CHANNEL)
            .isActive(true)
            .build();
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CONSUMER001")
            .name("Changed")
            .build();
        when(customerRepository.findById(2L)).thenReturn(Optional.of(consumer));

        assertThatThrownBy(() -> customerService.updateCustomer(2L, request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorKey")
            .isEqualTo(ErrorKeys.CUSTOMER_CONSUMER_READ_ONLY);
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("deleteCustomer soft delete")
    void testDeleteCustomerSuccessSoftDelete() {
        mockSecurityContext(1L, List.of(new SimpleGrantedAuthority("SUPER_ADMIN")));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

        CustomerResponse response = customerService.deleteCustomer(1L);

        assertThat(response).isNotNull();
        verify(customerRepository).save(argThat(customer -> !customer.getIsActive()));
    }

    @Test
    @DisplayName("deleteCustomer rejects channel-managed consumer")
    void testDeleteCustomerRejectsConsumer() {
        Customer consumer = Customer.builder()
            .id(2L)
            .code("CONSUMER001")
            .name("Channel Consumer")
            .customerType(CustomerType.CONSUMER)
            .source(CustomerSource.CHANNEL)
            .isActive(true)
            .build();
        when(customerRepository.findById(2L)).thenReturn(Optional.of(consumer));

        assertThatThrownBy(() -> customerService.deleteCustomer(2L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorKey")
            .isEqualTo(ErrorKeys.CUSTOMER_CONSUMER_READ_ONLY);
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("manual sales order requires an active client")
    void testValidateManualOrderCustomerRejectsConsumer() {
        Customer consumer = Customer.builder()
            .id(2L)
            .code("CONSUMER001")
            .name("Channel Consumer")
            .customerType(CustomerType.CONSUMER)
            .source(CustomerSource.CHANNEL)
            .isActive(true)
            .build();
        when(customerRepository.findById(2L)).thenReturn(Optional.of(consumer));

        assertThatThrownBy(() -> customerService.validateManualOrderCustomer(2L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorKey")
            .isEqualTo(ErrorKeys.MANUAL_ORDER_REQUIRES_CLIENT);
    }

    private void mockSecurityContext(Long userId, Collection<? extends GrantedAuthority> authorities) {
        User user = User.builder()
            .id(userId)
            .username("testuser")
            .password("password")
            .build();
        SecurityUser securityUser = new SecurityUser(user);

        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(securityUser);
        lenient().when(authentication.getAuthorities()).thenAnswer(invocation -> authorities);
        lenient().when(authentication.getName()).thenReturn("testuser");

        SecurityContextHolder.setContext(securityContext);
    }
}

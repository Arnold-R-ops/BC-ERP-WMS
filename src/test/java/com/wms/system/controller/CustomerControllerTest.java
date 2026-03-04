package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.customer.CreateCustomerRequest;
import com.wms.system.dto.customer.CustomerResponse;
import com.wms.system.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CustomerController Integration Test
 *
 * V3.7 Architecture: Customer Management Controller Tests
 *
 * Test Coverage:
 * 1. Create customer
 * 2. List all customers
 * 3. List active customers only
 * 4. Get customer details
 * 5. Update customer
 * 6. Delete customer (soft delete)
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.7 (Smart Sales and Outbound System)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("case-1")
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CustomerService customerService;

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:create"})
    @DisplayName("case-2")
    void testCreateCustomer() throws Exception {
        // Given
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("Test Customer")
            .contact("John Doe")
            .phone("13800138000")
            .email("john@example.com")
            .address("123 Test Street")
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .build();

        CustomerResponse response = CustomerResponse.builder()
            .id(1L)
            .code("CUST001")
            .name("Test Customer")
            .contact("John Doe")
            .phone("13800138000")
            .email("john@example.com")
            .address("123 Test Street")
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(customerService.createCustomer(any())).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.code").value("CUST001"))
            .andExpect(jsonPath("$.name").value("Test Customer"))
            .andExpect(jsonPath("$.contact").value("John Doe"))
            .andExpect(jsonPath("$.phone").value("13800138000"))
            .andExpect(jsonPath("$.email").value("john@example.com"))
            .andExpect(jsonPath("$.address").value("123 Test Street"))
            .andExpect(jsonPath("$.creditLimit").value(100000.00))
            .andExpect(jsonPath("$.isActive").value(true));

        verify(customerService).createCustomer(any());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:create"})
    @DisplayName("case-3")
    void testCreateCustomerInvalidEmail() throws Exception {
        // Given
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("Test Customer")
            .email("invalid-email")
            .build();

        // When & Then
        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isBadRequest());

        verify(customerService, never()).createCustomer(any());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:view"})
    @DisplayName("case-4")
    void testListCustomers() throws Exception {
        // Given
        List<CustomerResponse> responses = Arrays.asList(
            CustomerResponse.builder()
                .id(1L)
                .code("CUST001")
                .name("Customer A")
                .contact("Contact A")
                .phone("13800138001")
                .isActive(true)
                .build(),
            CustomerResponse.builder()
                .id(2L)
                .code("CUST002")
                .name("Customer B")
                .contact("Contact B")
                .phone("13800138002")
                .isActive(false)
                .build(),
            CustomerResponse.builder()
                .id(3L)
                .code("CUST003")
                .name("Customer C")
                .contact("Contact C")
                .phone("13800138003")
                .isActive(true)
                .build()
        );

        when(customerService.listCustomers()).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/customers")
                .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].code").value("CUST001"))
            .andExpect(jsonPath("$[0].isActive").value(true))
            .andExpect(jsonPath("$[1].code").value("CUST002"))
            .andExpect(jsonPath("$[1].isActive").value(false))
            .andExpect(jsonPath("$[2].code").value("CUST003"));

        verify(customerService).listCustomers();
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:view"})
    @DisplayName("case-5")
    void testListActiveCustomers() throws Exception {
        // Given
        List<CustomerResponse> responses = Arrays.asList(
            CustomerResponse.builder()
                .id(1L)
                .code("CUST001")
                .name("Customer A")
                .isActive(true)
                .build(),
            CustomerResponse.builder()
                .id(3L)
                .code("CUST003")
                .name("Customer C")
                .isActive(true)
                .build()
        );

        when(customerService.listActiveCustomers()).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/customers")
                .param("activeOnly", "true")
                .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].isActive").value(true))
            .andExpect(jsonPath("$[1].isActive").value(true));

        verify(customerService).listActiveCustomers();
        verify(customerService, never()).listCustomers();
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:view"})
    @DisplayName("case-6")
    void testGetCustomer() throws Exception {
        // Given
        CustomerResponse response = CustomerResponse.builder()
            .id(1L)
            .code("CUST001")
            .name("Test Customer")
            .contact("John Doe")
            .phone("13800138000")
            .email("john@example.com")
            .address("123 Test Street")
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(customerService.getCustomer(1L)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/customers/{id}", 1L)
                .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.code").value("CUST001"))
            .andExpect(jsonPath("$.name").value("Test Customer"))
            .andExpect(jsonPath("$.contact").value("John Doe"))
            .andExpect(jsonPath("$.phone").value("13800138000"))
            .andExpect(jsonPath("$.email").value("john@example.com"))
            .andExpect(jsonPath("$.creditLimit").value(100000.00));

        verify(customerService).getCustomer(1L);
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:edit"})
    @DisplayName("case-7")
    void testUpdateCustomer() throws Exception {
        // Given
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("Updated Customer Name")
            .contact("Jane Doe")
            .phone("13900139000")
            .email("jane@example.com")
            .address("456 New Street")
            .creditLimit(new BigDecimal("150000.00"))
            .isActive(true)
            .build();

        CustomerResponse response = CustomerResponse.builder()
            .id(1L)
            .code("CUST001")
            .name("Updated Customer Name")
            .contact("Jane Doe")
            .phone("13900139000")
            .email("jane@example.com")
            .address("456 New Street")
            .creditLimit(new BigDecimal("150000.00"))
            .isActive(true)
            .updatedAt(LocalDateTime.now())
            .build();

        when(customerService.updateCustomer(eq(1L), any())).thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/customers/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("Updated Customer Name"))
            .andExpect(jsonPath("$.contact").value("Jane Doe"))
            .andExpect(jsonPath("$.phone").value("13900139000"))
            .andExpect(jsonPath("$.email").value("jane@example.com"))
            .andExpect(jsonPath("$.creditLimit").value(150000.00));

        verify(customerService).updateCustomer(eq(1L), any());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:delete"})
    @DisplayName("case-8")
    void testDeleteCustomer() throws Exception {
        // Given
        doNothing().when(customerService).deleteCustomer(1L);

        // When & Then
        mockMvc.perform(delete("/api/customers/{id}", 1L))
            .andDo(print())
            .andExpect(status().isNoContent());

        verify(customerService).deleteCustomer(1L);
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:view"})
    @DisplayName("case-9")
    void testCreateCustomerWithoutPermission() throws Exception {
        // Given
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("Test Customer")
            .build();

        // When & Then
        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isForbidden());

        verify(customerService, never()).createCustomer(any());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:view"})
    @DisplayName("case-10")
    void testDeleteCustomerWithoutPermission() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/customers/{id}", 1L))
            .andDo(print())
            .andExpect(status().isForbidden());

        verify(customerService, never()).deleteCustomer(anyLong());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:create"})
    @DisplayName("case-11")
    void testCreateCustomerMissingFields() throws Exception {
        // Given - Missing required 'code' and 'name' fields
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .contact("John Doe")
            .phone("13800138000")
            .build();

        // When & Then
        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isBadRequest());

        verify(customerService, never()).createCustomer(any());
    }
}

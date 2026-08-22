package com.wms.system.controller;

import com.wms.system.dto.customer.CreateCustomerRequest;
import com.wms.system.dto.customer.CustomerResponse;
import com.wms.system.entity.enums.CustomerType;
import com.wms.system.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Customer Controller
 *
 * V3.7 Architecture: Customer Management RESTful API
 *
 * Provides endpoints for:
 * - Create customer
 * - List customers (all or active only)
 * - Get customer details
 * - Update customer
 * - Delete customer (soft delete)
 *
 * Business Flow:
 * 1. Create customer with unique code
 * 2. Maintain customer information (contact, phone, email, address)
 * 3. Manage customer status (active/inactive)
 * 4. Soft delete customer (set isActive = false)
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Slf4j
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /**
     * Create customer
     *
     * POST /api/customers
     *
     * Permission: customer:create
     *
     * Request Body: CreateCustomerRequest
     * Returns: CustomerResponse
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('customer:create', 'TENANT_ADMIN')")
    public ResponseEntity<CustomerResponse> createCustomer(
        @Valid @RequestBody CreateCustomerRequest request
    ) {
        log.info("API调用: createCustomer - code: {}, name: {}", request.getCode(), request.getName());

        CustomerResponse response = customerService.createCustomer(request);

        log.info("API响应: createCustomer - id: {}, code: {}, name: {}",
            response.getId(), response.getCode(), response.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all customers
     *
     * GET /api/customers
     *
     * Permission: customer:view
     *
     * Optional Parameters:
     * - activeOnly: Return only active customers (default: false)
     * - customerType: Filter by CLIENT or CONSUMER
     *
     * Returns: List<CustomerResponse>
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('customer:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<CustomerResponse>> listCustomers(
        @RequestParam(required = false) Boolean activeOnly,
        @RequestParam(required = false) CustomerType customerType
    ) {
        log.info("API调用: listCustomers - activeOnly: {}, customerType: {}", activeOnly, customerType);

        List<CustomerResponse> responses;

        if (Boolean.TRUE.equals(activeOnly)) {
            responses = customerType == null
                ? customerService.listActiveCustomers()
                : customerService.listActiveCustomers(customerType);
        } else {
            responses = customerType == null
                ? customerService.listCustomers()
                : customerService.listCustomers(customerType);
        }

        log.info("API响应: listCustomers - 客户数: {}", responses.size());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get customer by ID
     *
     * GET /api/customers/{id}
     *
     * Permission: customer:view
     *
     * Path Variable: id (Customer ID)
     * Returns: CustomerResponse
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('customer:view', 'TENANT_ADMIN')")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable Long id) {
        log.info("API调用: getCustomer - id: {}", id);

        CustomerResponse response = customerService.getCustomer(id);

        log.info("API响应: getCustomer - id: {}, code: {}, name: {}",
            response.getId(), response.getCode(), response.getName());

        return ResponseEntity.ok(response);
    }

    /**
     * Update customer
     *
     * PUT /api/customers/{id}
     *
     * Permission: customer:edit
     *
     * Path Variable: id (Customer ID)
     * Request Body: CreateCustomerRequest
     * Returns: CustomerResponse
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('customer:edit', 'TENANT_ADMIN')")
    public ResponseEntity<CustomerResponse> updateCustomer(
        @PathVariable Long id,
        @Valid @RequestBody CreateCustomerRequest request
    ) {
        log.info("API调用: updateCustomer - id: {}, name: {}", id, request.getName());

        CustomerResponse response = customerService.updateCustomer(id, request);

        log.info("API响应: updateCustomer - id: {}, code: {}, name: {}",
            response.getId(), response.getCode(), response.getName());

        return ResponseEntity.ok(response);
    }

    /**
     * Delete customer (soft delete)
     *
     * DELETE /api/customers/{id}
     *
     * Permission: customer:delete
     *
     * Path Variable: id (Customer ID)
     * Returns: 204 No Content
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('customer:delete', 'TENANT_ADMIN')")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        log.info("API调用: deleteCustomer - id: {}", id);

        customerService.deleteCustomer(id);

        log.info("API响应: deleteCustomer - id: {} (软删除成功)", id);

        return ResponseEntity.noContent().build();
    }
}

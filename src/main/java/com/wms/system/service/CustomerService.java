package com.wms.system.service;

import com.wms.system.dto.customer.CreateCustomerRequest;
import com.wms.system.dto.customer.CustomerResponse;
import com.wms.system.entity.Customer;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.CustomerRepository;
import com.wms.system.security.SecurityUser;
import com.wms.system.util.MaskingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客户管理服务
 *
 * V3.7 架构：客户管理模块
 * V4.1 架构：销售权限隔离与数据脱敏
 *
 * 核心职责：
 * 1. 客户创建、查询、更新、删除（软删除）
 * 2. 客户激活/停用管理
 * 3. 客户信息验证（编码唯一性、状态检查）
 * 4. 实体与 DTO 转换
 * 5. 业务异常处理（使用 Error Key 系统）
 * 6. 行级隔离（Row-Level Security）- V4.1
 * 7. 动态数据脱敏（Dynamic Masking）- V4.1
 *
 * 业务场景：
 * 1. 销售订单关联客户
 * 2. 客户主数据维护
 * 3. 客户信用额度管理（预留 ERP 扩展）
 * 4. 销售员只能查看和管理自己的客户（V4.1）
 * 5. 敏感信息自动脱敏（V4.1）
 *
 * 技术特性：
 * - @Transactional: 确保数据一致性
 * - Error Key System: 所有异常使用错误键，支持前端国际化
 * - Constructor Injection: 使用 @RequiredArgsConstructor 注入依赖
 * - DTO Pattern: 实体与 DTO 分离，保护内部数据结构
 * - Row-Level Security: 基于角色的数据隔离（V4.1）
 * - Dynamic Masking: 基于角色的动态脱敏（V4.1）
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 4.1 (Customer Data Security)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * 创建客户
     *
     * 业务流程：
     * 1. 验证客户编码唯一性
     * 2. 创建客户实体
     * 3. 保存到数据库
     * 4. 转换为响应 DTO
     *
     * 异常处理：
     * - CUSTOMER_ALREADY_EXISTS: 客户编码已存在
     *
     * @param request 创建客户请求
     * @return 客户响应 DTO
     * @throws BusinessException 如果客户编码已存在
     */
    @Transactional(rollbackFor = Exception.class)
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        log.info("Creating customer: code={}, name={}", request.getCode(), request.getName());

        // 验证客户编码唯一性
        if (customerRepository.existsByCode(request.getCode())) {
            log.error("Customer code already exists: {}", request.getCode());
            throw new BusinessException(
                ErrorKeys.CUSTOMER_ALREADY_EXISTS,
                Map.of("customerCode", request.getCode())
            );
        }

        // 获取当前登录用户ID（V4.1）
        Long currentUserId = getCurrentUserId();

        // 创建客户实体
        Customer customer = Customer.builder()
            .code(request.getCode())
            .name(request.getName())
            .contact(request.getContact())
            .phone(request.getPhone())
            .email(request.getEmail())
            .address(request.getAddress())
            .creditLimit(request.getCreditLimit() != null ? request.getCreditLimit() : BigDecimal.ZERO)
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .ownerId(currentUserId)  // V4.1: 自动设置归属人
            .build();

        // 保存到数据库
        Customer savedCustomer = customerRepository.save(customer);
        log.info("Customer created successfully: id={}, code={}, ownerId={}",
            savedCustomer.getId(), savedCustomer.getCode(), savedCustomer.getOwnerId());

        // 转换为响应 DTO（不脱敏，因为是创建者本人）
        return convertToResponse(savedCustomer, false);
    }

    /**
     * 更新客户信息
     *
     * 业务规则：
     * - 客户编码不可修改（业务主键）
     * - 可以修改其他所有字段
     *
     * V4.1 修改保护：
     * - 如果字段包含掩码字符（****），则忽略该字段的更新
     * - 保持数据库原值不变
     * - 防止脱敏数据覆盖原始数据
     *
     * 异常处理：
     * - CUSTOMER_NOT_FOUND: 客户不存在
     *
     * @param id 客户ID
     * @param request 更新客户请求
     * @return 更新后的客户响应 DTO
     * @throws BusinessException 如果客户不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public CustomerResponse updateCustomer(Long id, CreateCustomerRequest request) {
        log.info("Updating customer: id={}", id);

        // 查询客户
        Customer customer = getCustomerEntityById(id);

        // 更新字段（编码不可修改）
        customer.setName(request.getName());
        customer.setContact(request.getContact());

        // V4.1: 修改保护 - 如果包含掩码，忽略更新
        if (!MaskingUtils.isMasked(request.getPhone())) {
            customer.setPhone(request.getPhone());
        } else {
            log.debug("Phone contains mask, skipping update for customer: id={}", id);
        }

        if (!MaskingUtils.isMasked(request.getEmail())) {
            customer.setEmail(request.getEmail());
        } else {
            log.debug("Email contains mask, skipping update for customer: id={}", id);
        }

        if (!MaskingUtils.isMasked(request.getAddress())) {
            customer.setAddress(request.getAddress());
        } else {
            log.debug("Address contains mask, skipping update for customer: id={}", id);
        }

        if (request.getCreditLimit() != null) {
            customer.setCreditLimit(request.getCreditLimit());
        }

        if (request.getIsActive() != null) {
            customer.setIsActive(request.getIsActive());
        }

        // 保存更新
        Customer updatedCustomer = customerRepository.save(customer);
        log.info("Customer updated successfully: id={}, code={}", updatedCustomer.getId(), updatedCustomer.getCode());

        // V4.1: 动态脱敏
        boolean shouldMask = isSalesRole() && !isManagerOrAdmin();

        return convertToResponse(updatedCustomer, shouldMask);
    }

    /**
     * 根据ID查询客户
     *
     * V4.1 动态脱敏：
     * - SALES 角色：返回脱敏数据
     * - ADMIN/MANAGER 角色：返回原始数据
     *
     * 异常处理：
     * - CUSTOMER_NOT_FOUND: 客户不存在
     *
     * @param id 客户ID
     * @return 客户响应 DTO（根据角色决定是否脱敏）
     * @throws BusinessException 如果客户不存在
     */
    public CustomerResponse getCustomer(Long id) {
        log.debug("Querying customer by id: {}", id);

        Customer customer = getCustomerEntityById(id);

        // V4.1: 动态脱敏
        boolean shouldMask = isSalesRole() && !isManagerOrAdmin();

        return convertToResponse(customer, shouldMask);
    }

    /**
     * 根据编码查询客户
     *
     * V4.1 动态脱敏：
     * - SALES 角色：返回脱敏数据
     * - ADMIN/MANAGER 角色：返回原始数据
     *
     * 异常处理：
     * - CUSTOMER_NOT_FOUND: 客户不存在
     *
     * @param code 客户编码
     * @return 客户响应 DTO（根据角色决定是否脱敏）
     * @throws BusinessException 如果客户不存在
     */
    public CustomerResponse getCustomerByCode(String code) {
        log.debug("Querying customer by code: {}", code);

        Customer customer = customerRepository.findByCode(code)
            .orElseThrow(() -> {
                log.error("Customer not found: code={}", code);
                return new BusinessException(
                    ErrorKeys.CUSTOMER_NOT_FOUND,
                    Map.of("customerCode", code)
                );
            });

        // V4.1: 动态脱敏
        boolean shouldMask = isSalesRole() && !isManagerOrAdmin();

        return convertToResponse(customer, shouldMask);
    }

    /**
     * 查询所有客户
     *
     * V4.1 行级隔离：
     * - SALES 角色：只能查看自己的客户
     * - ADMIN/MANAGER 角色：可以查看所有客户
     *
     * 使用场景：
     * 1. 客户管理界面（显示所有客户）
     * 2. 统计报表（包含历史数据）
     *
     * @return 客户列表（根据角色过滤）
     */
    public List<CustomerResponse> listCustomers() {
        log.debug("Querying all customers");

        List<Customer> customers;

        // V4.1: 行级隔离
        if (isSalesRole() && !isManagerOrAdmin()) {
            // 销售员只能查看自己的客户
            Long currentUserId = getCurrentUserId();
            customers = customerRepository.findByOwnerId(currentUserId);
            log.debug("Sales user {} querying own customers: count={}", currentUserId, customers.size());
        } else {
            // 管理员和经理可以查看所有客户
            customers = customerRepository.findAll();
            log.debug("Admin/Manager querying all customers: count={}", customers.size());
        }

        // V4.1: 动态脱敏
        boolean shouldMask = isSalesRole() && !isManagerOrAdmin();

        return customers.stream()
            .map(customer -> convertToResponse(customer, shouldMask))
            .collect(Collectors.toList());
    }

    /**
     * 查询所有激活的客户
     *
     * V4.1 行级隔离：
     * - SALES 角色：只能查看自己的激活客户
     * - ADMIN/MANAGER 角色：可以查看所有激活客户
     *
     * 使用场景：
     * 1. 下拉选择框（选择客户）
     * 2. 销售订单创建（只显示激活的客户）
     *
     * @return 激活客户列表（根据角色过滤）
     */
    public List<CustomerResponse> listActiveCustomers() {
        log.debug("Querying all active customers");

        List<Customer> customers;

        // V4.1: 行级隔离
        if (isSalesRole() && !isManagerOrAdmin()) {
            // 销售员只能查看自己的激活客户
            Long currentUserId = getCurrentUserId();
            customers = customerRepository.findByOwnerIdAndIsActiveTrue(currentUserId);
            log.debug("Sales user {} querying own active customers: count={}", currentUserId, customers.size());
        } else {
            // 管理员和经理可以查看所有激活客户
            customers = customerRepository.findByIsActiveTrue();
            log.debug("Admin/Manager querying all active customers: count={}", customers.size());
        }

        // V4.1: 动态脱敏
        boolean shouldMask = isSalesRole() && !isManagerOrAdmin();

        return customers.stream()
            .map(customer -> convertToResponse(customer, shouldMask))
            .collect(Collectors.toList());
    }

    /**
     * 删除客户（软删除）
     *
     * 业务规则：
     * - 不物理删除数据，只设置 isActive = false
     * - 保留历史记录和关联数据
     * - 可以重新激活
     *
     * 异常处理：
     * - CUSTOMER_NOT_FOUND: 客户不存在
     *
     * @param id 客户ID
     * @return 删除后的客户响应 DTO
     * @throws BusinessException 如果客户不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public CustomerResponse deleteCustomer(Long id) {
        log.info("Deleting customer (soft delete): id={}", id);

        Customer customer = getCustomerEntityById(id);
        customer.setIsActive(false);

        Customer deletedCustomer = customerRepository.save(customer);
        log.info("Customer deleted successfully (soft delete): id={}, code={}", deletedCustomer.getId(), deletedCustomer.getCode());

        // V4.1: 动态脱敏
        boolean shouldMask = isSalesRole() && !isManagerOrAdmin();

        return convertToResponse(deletedCustomer, shouldMask);
    }

    /**
     * 验证客户是否激活
     *
     * 使用场景：
     * 1. 创建销售订单前验证客户状态
     * 2. 业务操作前的前置检查
     *
     * 异常处理：
     * - CUSTOMER_NOT_FOUND: 客户不存在
     * - CUSTOMER_INACTIVE: 客户已停用
     *
     * @param customerId 客户ID
     * @throws BusinessException 如果客户不存在或已停用
     */
    public void validateCustomerActive(Long customerId) {
        log.debug("Validating customer active status: id={}", customerId);

        Customer customer = getCustomerEntityById(customerId);

        if (!customer.getIsActive()) {
            log.error("Customer is inactive: id={}, code={}", customerId, customer.getCode());
            throw new BusinessException(
                ErrorKeys.CUSTOMER_INACTIVE,
                Map.of(
                    "customerId", customerId,
                    "customerCode", customer.getCode()
                )
            );
        }
    }

    /**
     * 根据ID查询客户实体（内部方法）
     *
     * 异常处理：
     * - CUSTOMER_NOT_FOUND: 客户不存在
     *
     * @param id 客户ID
     * @return 客户实体
     * @throws BusinessException 如果客户不存在
     */
    private Customer getCustomerEntityById(Long id) {
        return customerRepository.findById(id)
            .orElseThrow(() -> {
                log.error("Customer not found: id={}", id);
                return new BusinessException(
                    ErrorKeys.CUSTOMER_NOT_FOUND,
                    Map.of("customerId", id)
                );
            });
    }

    /**
     * 将客户实体转换为响应 DTO
     *
     * 说明：
     * - 隔离内部实体结构，避免直接暴露给前端
     * - 支持字段选择和格式化
     * - 便于版本演进和字段调整
     *
     * @param customer 客户实体
     * @return 客户响应 DTO
     */
    /**
     * 将客户实体转换为响应 DTO
     *
     * V4.1 动态脱敏：
     * - 根据 shouldMask 参数决定是否脱敏
     * - SALES 角色：脱敏敏感字段（name, phone, email, address）
     * - ADMIN/MANAGER 角色：返回原始数据
     *
     * 说明：
     * - 隔离内部实体结构，避免直接暴露给前端
     * - 支持字段选择和格式化
     * - 便于版本演进和字段调整
     *
     * @param customer 客户实体
     * @param shouldMask 是否需要脱敏
     * @return 客户响应 DTO
     */
    private CustomerResponse convertToResponse(Customer customer, boolean shouldMask) {
        if (shouldMask) {
            // V4.1: 脱敏处理
            return CustomerResponse.builder()
                .id(customer.getId())
                .code(customer.getCode())
                .name(MaskingUtils.maskName(customer.getName()))
                .contact(customer.getContact())
                .phone(MaskingUtils.maskPhone(customer.getPhone()))
                .email(MaskingUtils.maskEmail(customer.getEmail()))
                .address(MaskingUtils.maskAddress(customer.getAddress()))
                .creditLimit(customer.getCreditLimit())
                .isActive(customer.getIsActive())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
        } else {
            // 返回原始数据
            return CustomerResponse.builder()
                .id(customer.getId())
                .code(customer.getCode())
                .name(customer.getName())
                .contact(customer.getContact())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .address(customer.getAddress())
                .creditLimit(customer.getCreditLimit())
                .isActive(customer.getIsActive())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
        }
    }

    // ========== V4.1 辅助方法 ==========

    /**
     * 获取当前登录用户ID
     *
     * @return 当前用户ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser) {
            SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
            return securityUser.getId();
        }
        return null;
    }

    /**
     * 判断当前用户是否为销售角色
     *
     * @return 如果是销售角色返回 true
     */
    private boolean isSalesRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals("SALESPERSON") || auth.equals("SALES"));
        }
        return false;
    }

    /**
     * 判断当前用户是否为管理员或经理
     *
     * @return 如果是管理员或经理返回 true
     */
    private boolean isManagerOrAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth ->
                    auth.equals("SUPER_ADMIN") ||
                    auth.equals("WAREHOUSE_ADMIN") ||
                    auth.equals("CHAIRMAN") ||
                    auth.equals("MANAGER")
                );
        }
        return false;
    }
}

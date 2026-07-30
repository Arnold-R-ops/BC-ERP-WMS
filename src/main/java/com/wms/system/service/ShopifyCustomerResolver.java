package com.wms.system.service;

import com.wms.system.dto.shopify.ShopifyAddressDto;
import com.wms.system.dto.shopify.ShopifyCustomerDto;
import com.wms.system.dto.shopify.ShopifyOrderDto;
import com.wms.system.entity.Customer;
import com.wms.system.entity.IntegrationConfig;
import com.wms.system.entity.enums.CustomerSource;
import com.wms.system.entity.enums.CustomerType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shopify-specific customer identity strategy.
 *
 * Automatic ingestion may provision a lightweight consumer only when the
 * store explicitly enables retail mode. Reconciliation repair always calls
 * the match-only entry point and therefore cannot create customer master data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyCustomerResolver {

    private static final long DEFAULT_COMPANY_ID = 1L;
    private static final String CHANNEL = "SHOPIFY";

    private final CustomerRepository customerRepository;

    Customer resolveForAutomatic(ShopifyOrderDto order, IntegrationConfig config) {
        Customer existing = findExisting(order);
        if (existing != null) {
            return existing;
        }

        if (!Boolean.TRUE.equals(config.getRetailMode())) {
            throw unmatched(order, "Retail mode is disabled; an existing active customer is required");
        }
        if (!hasIdentityAnchor(order)) {
            throw unmatched(order, "Shopify customer.id or email is required");
        }

        Customer created = customerRepository.save(Customer.builder()
            .code(generateConsumerCode())
            .name(resolveDisplayName(order))
            .email(resolveEmail(order))
            .normalizedEmail(Customer.normalizeEmail(resolveEmail(order)))
            .externalCustomerId(resolveExternalCustomerId(order))
            .customerType(CustomerType.CONSUMER)
            .source(CustomerSource.CHANNEL)
            .creditLimit(BigDecimal.ZERO)
            .isActive(true)
            .isDeleted(false)
            .build());

        log.info("Created lightweight Shopify consumer: customerId={}, externalCustomerId={}",
            created.getId(), created.getExternalCustomerId());
        return created;
    }

    public Customer resolveExistingOnly(ShopifyOrderDto order) {
        Customer existing = findExisting(order);
        if (existing == null) {
            throw unmatched(order, "No unique active customer match; repair cannot create customers");
        }
        return existing;
    }

    private Customer findExisting(ShopifyOrderDto order) {
        String externalCustomerId = resolveExternalCustomerId(order);
        String email = resolveEmail(order);
        String normalizedEmail = Customer.normalizeEmail(email);

        if (StringUtils.hasText(externalCustomerId)) {
            Customer externalMatch = customerRepository
                .findByCompanyIdAndCustomerTypeAndExternalCustomerId(
                    DEFAULT_COMPANY_ID,
                    CustomerType.CONSUMER,
                    externalCustomerId
                )
                .orElse(null);
            if (externalMatch != null) {
                requireActive(externalMatch);
                updateConsumerIdentity(externalMatch, email, normalizedEmail, externalCustomerId);
                return externalMatch;
            }
        }

        if (!StringUtils.hasText(normalizedEmail)) {
            return null;
        }

        List<Customer> allMatches = customerRepository
            .findByCompanyIdAndNormalizedEmailOrderByIdAsc(DEFAULT_COMPANY_ID, normalizedEmail);
        List<Customer> activeMatches = allMatches.stream()
            .filter(customer -> Boolean.TRUE.equals(customer.getIsActive()))
            .toList();

        if (activeMatches.size() > 1) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of(
                    "field", "customer.email",
                    "value", normalizedEmail,
                    "constraint", "Multiple active customers match this email; automatic guessing is forbidden",
                    "matchCount", activeMatches.size()
                )
            );
        }
        if (activeMatches.size() == 1) {
            Customer customer = activeMatches.get(0);
            updateConsumerIdentity(customer, email, normalizedEmail, externalCustomerId);
            return customer;
        }
        if (!allMatches.isEmpty()) {
            requireActive(allMatches.get(0));
        }
        return null;
    }

    private void updateConsumerIdentity(
        Customer customer,
        String email,
        String normalizedEmail,
        String externalCustomerId
    ) {
        if (customer.getCustomerType() != CustomerType.CONSUMER) {
            return;
        }

        boolean changed = false;
        if (StringUtils.hasText(normalizedEmail)
                && !normalizedEmail.equals(customer.getNormalizedEmail())) {
            customer.setEmail(email.trim());
            customer.setNormalizedEmail(normalizedEmail);
            changed = true;
        }
        if (!StringUtils.hasText(customer.getExternalCustomerId())
                && StringUtils.hasText(externalCustomerId)) {
            customer.setExternalCustomerId(externalCustomerId);
            changed = true;
        }
        if (changed) {
            customerRepository.save(customer);
        }
    }

    private void requireActive(Customer customer) {
        if (!Boolean.TRUE.equals(customer.getIsActive())) {
            throw new BusinessException(
                ErrorKeys.CUSTOMER_INACTIVE,
                Map.of("customerId", customer.getId(), "customerCode", customer.getCode())
            );
        }
    }

    private boolean hasIdentityAnchor(ShopifyOrderDto order) {
        return StringUtils.hasText(resolveExternalCustomerId(order))
            || StringUtils.hasText(resolveEmail(order));
    }

    static String resolveExternalCustomerId(ShopifyOrderDto order) {
        ShopifyCustomerDto customer = order == null ? null : order.getCustomer();
        return customer != null && customer.getId() != null
            ? String.valueOf(customer.getId())
            : null;
    }

    static String resolveEmail(ShopifyOrderDto order) {
        if (order == null) {
            return null;
        }
        if (StringUtils.hasText(order.getEmail())) {
            return order.getEmail().trim();
        }
        if (StringUtils.hasText(order.getContactEmail())) {
            return order.getContactEmail().trim();
        }
        ShopifyCustomerDto customer = order.getCustomer();
        return customer != null && StringUtils.hasText(customer.getEmail())
            ? customer.getEmail().trim()
            : null;
    }

    static String resolveDisplayName(ShopifyOrderDto order) {
        ShopifyCustomerDto customer = order == null ? null : order.getCustomer();
        String customerName = customer == null
            ? null
            : joinName(customer.getFirstName(), customer.getLastName());
        if (StringUtils.hasText(customerName)) {
            return customerName;
        }

        ShopifyAddressDto address = order == null ? null : order.getShippingAddress();
        if (address != null && StringUtils.hasText(address.getName())) {
            return address.getName().trim();
        }
        if (address != null) {
            String addressName = joinName(address.getFirstName(), address.getLastName());
            if (StringUtils.hasText(addressName)) {
                return addressName;
            }
        }

        String email = resolveEmail(order);
        return StringUtils.hasText(email) ? email : "Shopify Consumer";
    }

    private static String joinName(String firstName, String lastName) {
        String first = StringUtils.hasText(firstName) ? firstName.trim() : "";
        String last = StringUtils.hasText(lastName) ? lastName.trim() : "";
        String value = (first + " " + last).trim();
        return value.isEmpty() ? null : value;
    }

    private String generateConsumerCode() {
        return "C-" + CHANNEL + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private BusinessException unmatched(ShopifyOrderDto order, String constraint) {
        String orderNo = order == null || !StringUtils.hasText(order.getName())
            ? "(unknown)"
            : order.getName();
        return new BusinessException(
            ErrorKeys.VALIDATION_FAILED,
            Map.of(
                "field", "customerIdentity",
                "value", orderNo,
                "constraint", constraint
            )
        );
    }
}

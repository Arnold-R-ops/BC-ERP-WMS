package com.wms.system.service;

import com.wms.system.dto.shopify.ShopifyOrderDto;
import com.wms.system.entity.Customer;
import com.wms.system.entity.IntegrationConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional worker behind {@link ShopifyConsumerIdentityService}.
 *
 * Kept separate so the facade can hold its identity lock until this proxied
 * method has returned and the independent transaction has committed.
 */
@Service
@RequiredArgsConstructor
class ShopifyConsumerIdentityTransactionService {

    private final ShopifyCustomerResolver customerResolver;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer resolveAndCommit(ShopifyOrderDto order, IntegrationConfig config) {
        return customerResolver.resolveForAutomatic(order, config);
    }
}

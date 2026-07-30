package com.wms.system.service;

import com.wms.system.dto.shopify.ShopifyOrderDto;
import com.wms.system.entity.Customer;
import com.wms.system.entity.IntegrationConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Owns the durable identity boundary for Shopify retail consumers.
 *
 * Customer identity is committed before the separate order-fulfilment
 * transaction starts. This service is intentionally the only automatic
 * ingestion entry point allowed to provision a lightweight consumer.
 */
@Service
@RequiredArgsConstructor
public class ShopifyConsumerIdentityService {

    private static final int LOCK_STRIPES = 256;
    private static final Object[] IDENTITY_LOCKS = createLocks();

    private final ShopifyConsumerIdentityTransactionService identityTransactionService;

    public Customer resolveForAutomaticIngestion(ShopifyOrderDto order, IntegrationConfig config) {
        /*
         * Serialize the same identity within one application instance so two
         * simultaneous deliveries do not both attempt an insert. The database
         * unique indexes on (company, external customer id) and
         * (company, normalized email) remain the cross-instance safety net.
         */
        synchronized (lockFor(order)) {
            return identityTransactionService.resolveAndCommit(order, config);
        }
    }

    private Object lockFor(ShopifyOrderDto order) {
        String externalId = ShopifyCustomerResolver.resolveExternalCustomerId(order);
        String email = Customer.normalizeEmail(ShopifyCustomerResolver.resolveEmail(order));
        String anchor = externalId != null ? "id:" + externalId : "email:" + email;
        return IDENTITY_LOCKS[Math.floorMod(anchor.hashCode(), LOCK_STRIPES)];
    }

    private static Object[] createLocks() {
        Object[] locks = new Object[LOCK_STRIPES];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
        return locks;
    }
}

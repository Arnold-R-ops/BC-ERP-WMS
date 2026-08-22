package com.wms.system.subscription.repository;

import com.wms.system.subscription.model.TenantSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, Long> {
    Optional<TenantSubscription> findByTenantId(Long tenantId);
}

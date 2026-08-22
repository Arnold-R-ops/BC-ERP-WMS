package com.wms.system.subscription.repository;

import com.wms.system.subscription.model.PlanCode;
import com.wms.system.subscription.model.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    Optional<SubscriptionPlan> findByPlanCodeAndActiveTrue(PlanCode planCode);
}

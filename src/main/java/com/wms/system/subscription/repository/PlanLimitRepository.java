package com.wms.system.subscription.repository;

import com.wms.system.subscription.model.PlanLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanLimitRepository extends JpaRepository<PlanLimit, Long> {
    List<PlanLimit> findAllByPlanId(Long planId);
}

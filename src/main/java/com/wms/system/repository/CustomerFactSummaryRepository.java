package com.wms.system.repository;

import com.wms.system.entity.CustomerFactSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerFactSummaryRepository extends JpaRepository<CustomerFactSummary, Long> {

    Optional<CustomerFactSummary> findByCompanyIdAndCustomerId(Long companyId, Long customerId);
}

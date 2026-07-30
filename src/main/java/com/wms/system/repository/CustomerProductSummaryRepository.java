package com.wms.system.repository;

import com.wms.system.entity.CustomerProductSummary;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerProductSummaryRepository extends JpaRepository<CustomerProductSummary, Long> {

    List<CustomerProductSummary> findByCompanyIdAndCustomerIdOrderByTotalAmountDescTotalQuantityDesc(
        Long companyId,
        Long customerId,
        Pageable pageable
    );
}

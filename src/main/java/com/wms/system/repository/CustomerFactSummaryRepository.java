package com.wms.system.repository;

import com.wms.system.entity.CustomerFactSummary;
import com.wms.system.entity.enums.CustomerSource;
import com.wms.system.entity.enums.CustomerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerFactSummaryRepository extends JpaRepository<CustomerFactSummary, Long> {

    Optional<CustomerFactSummary> findByCompanyIdAndCustomerId(Long companyId, Long customerId);

    @Query("""
        SELECT summary
        FROM CustomerFactSummary summary
        WHERE summary.companyId = :companyId
          AND EXISTS (
              SELECT customer.id
              FROM Customer customer
              WHERE customer.id = summary.customerId
                AND customer.companyId = :companyId
                AND (
                    :keyword = ''
                    OR LOWER(customer.code) LIKE CONCAT('%', :keyword, '%')
                    OR LOWER(customer.name) LIKE CONCAT('%', :keyword, '%')
                )
                AND (:customerType IS NULL OR customer.customerType = :customerType)
                AND (:source IS NULL OR customer.source = :source)
          )
        """)
    Page<CustomerFactSummary> search(
        @Param("companyId") Long companyId,
        @Param("keyword") String keyword,
        @Param("customerType") CustomerType customerType,
        @Param("source") CustomerSource source,
        Pageable pageable
    );
}

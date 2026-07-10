package com.wms.system.repository;

import com.wms.system.entity.SalesDailySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SalesDailySummaryRepository extends JpaRepository<SalesDailySummary, Long> {

    List<SalesDailySummary> findByCompanyIdAndSummaryDateBetweenOrderBySummaryDateAsc(
        Long companyId,
        LocalDate startDate,
        LocalDate endDate
    );
}

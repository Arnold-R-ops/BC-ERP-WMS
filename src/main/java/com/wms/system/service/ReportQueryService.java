package com.wms.system.service;

import com.wms.system.dto.report.CustomerFactSummaryResponse;
import com.wms.system.dto.report.SalesDailySummaryResponse;
import com.wms.system.entity.CustomerFactSummary;
import com.wms.system.entity.SalesDailySummary;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.CustomerFactSummaryRepository;
import com.wms.system.repository.CustomerRepository;
import com.wms.system.repository.SalesDailySummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportQueryService {

    private static final Long DEFAULT_COMPANY_ID = 1L;

    private final CustomerRepository customerRepository;
    private final CustomerFactSummaryRepository customerFactSummaryRepository;
    private final SalesDailySummaryRepository salesDailySummaryRepository;

    @Transactional(readOnly = true)
    public CustomerFactSummaryResponse getCustomerFactSummary(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new BusinessException(
                ErrorKeys.CUSTOMER_NOT_FOUND,
                Map.of("customerId", customerId)
            );
        }

        return customerFactSummaryRepository.findByCompanyIdAndCustomerId(DEFAULT_COMPANY_ID, customerId)
            .map(this::toCustomerResponse)
            .orElseGet(() -> emptyCustomerResponse(customerId));
    }

    @Transactional(readOnly = true)
    public List<SalesDailySummaryResponse> listSalesDailySummary(LocalDate startDate, LocalDate endDate) {
        return salesDailySummaryRepository
            .findByCompanyIdAndSummaryDateBetweenOrderBySummaryDateAsc(DEFAULT_COMPANY_ID, startDate, endDate)
            .stream()
            .map(this::toSalesDailyResponse)
            .toList();
    }

    private CustomerFactSummaryResponse toCustomerResponse(CustomerFactSummary summary) {
        return CustomerFactSummaryResponse.builder()
            .customerId(summary.getCustomerId())
            .totalOrderCount(summary.getTotalOrderCount())
            .totalAmount(summary.getTotalAmount())
            .lastOrderDate(summary.getLastOrderDate())
            .averageIntervalDays(summary.getAverageIntervalDays())
            .refreshedAt(summary.getRefreshedAt())
            .build();
    }

    private CustomerFactSummaryResponse emptyCustomerResponse(Long customerId) {
        return CustomerFactSummaryResponse.builder()
            .customerId(customerId)
            .totalOrderCount(0L)
            .totalAmount(BigDecimal.ZERO)
            .averageIntervalDays(BigDecimal.ZERO)
            .build();
    }

    private SalesDailySummaryResponse toSalesDailyResponse(SalesDailySummary summary) {
        return SalesDailySummaryResponse.builder()
            .summaryDate(summary.getSummaryDate())
            .totalOrderCount(summary.getTotalOrderCount())
            .totalAmount(summary.getTotalAmount())
            .refreshedAt(summary.getRefreshedAt())
            .build();
    }
}

package com.wms.system.service;

import com.wms.system.dto.report.CustomerFactSummaryResponse;
import com.wms.system.dto.report.CustomerFactListItemResponse;
import com.wms.system.dto.report.CustomerProductSummaryResponse;
import com.wms.system.dto.report.SalesDailySummaryResponse;
import com.wms.system.dto.report.SalesOverviewResponse;
import com.wms.system.entity.Customer;
import com.wms.system.entity.CustomerFactSummary;
import com.wms.system.entity.CustomerProductSummary;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.SalesDailySummary;
import com.wms.system.entity.enums.CustomerSource;
import com.wms.system.entity.enums.CustomerType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.CustomerFactSummaryRepository;
import com.wms.system.repository.CustomerProductSummaryRepository;
import com.wms.system.repository.CustomerRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.SalesDailySummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportQueryService {

    private static final Long DEFAULT_COMPANY_ID = 1L;

    private final CustomerRepository customerRepository;
    private final CustomerFactSummaryRepository customerFactSummaryRepository;
    private final CustomerProductSummaryRepository customerProductSummaryRepository;
    private final ProductSkuRepository productSkuRepository;
    private final SalesDailySummaryRepository salesDailySummaryRepository;

    @Transactional(readOnly = true)
    public CustomerFactSummaryResponse getCustomerFactSummary(Long customerId) {
        return getCustomerFactSummary(customerId, 5);
    }

    @Transactional(readOnly = true)
    public CustomerFactSummaryResponse getCustomerFactSummary(Long customerId, int topProducts) {
        Customer customer = findCustomer(customerId);
        CustomerFactSummary summary = customerFactSummaryRepository
            .findByCompanyIdAndCustomerId(DEFAULT_COMPANY_ID, customerId)
            .orElse(null);
        List<CustomerProductSummaryResponse> productFacts = listTopProducts(customerId, topProducts);

        if (summary == null) {
            return emptyCustomerResponse(customer, productFacts);
        }

        return toCustomerResponse(summary, customer, productFacts);
    }

    @Transactional(readOnly = true)
    public Page<CustomerFactListItemResponse> listCustomerFacts(
        String keyword,
        CustomerType customerType,
        CustomerSource source,
        String sortBy,
        String sortDirection,
        int page,
        int size
    ) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection)
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
        String normalizedSort = normalizeCustomerSort(sortBy);
        PageRequest pageable = PageRequest.of(
            normalizedPage,
            normalizedSize,
            Sort.by(direction, normalizedSort).and(Sort.by(Sort.Direction.ASC, "customerId"))
        );

        Page<CustomerFactSummary> summaries = customerFactSummaryRepository.search(
            DEFAULT_COMPANY_ID,
            normalizeKeyword(keyword),
            customerType,
            source,
            pageable
        );
        Map<Long, Customer> customers = loadCustomers(
            summaries.getContent().stream().map(CustomerFactSummary::getCustomerId).toList()
        );

        return summaries.map(summary -> toCustomerListItem(summary, customers.get(summary.getCustomerId())));
    }

    @Transactional(readOnly = true)
    public List<SalesDailySummaryResponse> listSalesDailySummary(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return salesDailySummaryRepository
            .findByCompanyIdAndSummaryDateBetweenOrderBySummaryDateAsc(DEFAULT_COMPANY_ID, startDate, endDate)
            .stream()
            .map(this::toSalesDailyResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public SalesOverviewResponse getSalesOverview(LocalDate startDate, LocalDate endDate) {
        List<SalesDailySummaryResponse> dailyRows = listSalesDailySummary(startDate, endDate);
        long totalOrderCount = dailyRows.stream().mapToLong(row -> safeLong(row.getTotalOrderCount())).sum();
        BigDecimal totalAmount = dailyRows.stream()
            .map(SalesDailySummaryResponse::getTotalAmount)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SalesOverviewResponse.builder()
            .startDate(startDate)
            .endDate(endDate)
            .totalOrderCount(totalOrderCount)
            .totalAmount(totalAmount)
            .averageOrderValue(averageAmount(totalAmount, totalOrderCount))
            .draftCount(sumStatus(dailyRows, SalesDailySummaryResponse::getDraftCount))
            .pendingApprovalCount(sumStatus(dailyRows, SalesDailySummaryResponse::getPendingApprovalCount))
            .approvedAwaitingShipmentCount(
                sumStatus(dailyRows, SalesDailySummaryResponse::getApprovedAwaitingShipmentCount)
            )
            .shippedCount(sumStatus(dailyRows, SalesDailySummaryResponse::getShippedCount))
            .rejectedCount(sumStatus(dailyRows, SalesDailySummaryResponse::getRejectedCount))
            .cancelledCount(sumStatus(dailyRows, SalesDailySummaryResponse::getCancelledCount))
            .voidedCount(sumStatus(dailyRows, SalesDailySummaryResponse::getVoidedCount))
            .refreshedAt(dailyRows.stream()
                .map(SalesDailySummaryResponse::getRefreshedAt)
                .filter(value -> value != null)
                .max(LocalDateTime::compareTo)
                .orElse(null))
            .build();
    }

    private Customer findCustomer(Long customerId) {
        return customerRepository.findById(customerId)
            .filter(customer -> DEFAULT_COMPANY_ID.equals(customer.getCompanyId()))
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.CUSTOMER_NOT_FOUND,
                Map.of("customerId", customerId)
            ));
    }

    private CustomerFactSummaryResponse toCustomerResponse(
        CustomerFactSummary summary,
        Customer customer,
        List<CustomerProductSummaryResponse> topProducts
    ) {
        return CustomerFactSummaryResponse.builder()
            .customerId(summary.getCustomerId())
            .customerCode(customer.getCode())
            .customerName(customer.getName())
            .customerType(customer.getCustomerType())
            .source(customer.getSource())
            .totalOrderCount(summary.getTotalOrderCount())
            .totalAmount(summary.getTotalAmount())
            .averageOrderValue(averageAmount(summary.getTotalAmount(), summary.getTotalOrderCount()))
            .lastOrderDate(summary.getLastOrderDate())
            .averageIntervalDays(summary.getAverageIntervalDays())
            .refreshedAt(summary.getRefreshedAt())
            .topProducts(topProducts)
            .build();
    }

    private CustomerFactSummaryResponse emptyCustomerResponse(
        Customer customer,
        List<CustomerProductSummaryResponse> topProducts
    ) {
        return CustomerFactSummaryResponse.builder()
            .customerId(customer.getId())
            .customerCode(customer.getCode())
            .customerName(customer.getName())
            .customerType(customer.getCustomerType())
            .source(customer.getSource())
            .totalOrderCount(0L)
            .totalAmount(BigDecimal.ZERO)
            .averageOrderValue(BigDecimal.ZERO)
            .averageIntervalDays(BigDecimal.ZERO)
            .topProducts(topProducts)
            .build();
    }

    private CustomerFactListItemResponse toCustomerListItem(
        CustomerFactSummary summary,
        Customer customer
    ) {
        return CustomerFactListItemResponse.builder()
            .customerId(summary.getCustomerId())
            .customerCode(customer == null ? null : customer.getCode())
            .customerName(customer == null ? null : customer.getName())
            .customerType(customer == null ? null : customer.getCustomerType())
            .source(customer == null ? null : customer.getSource())
            .totalOrderCount(summary.getTotalOrderCount())
            .totalAmount(summary.getTotalAmount())
            .averageOrderValue(averageAmount(summary.getTotalAmount(), summary.getTotalOrderCount()))
            .lastOrderDate(summary.getLastOrderDate())
            .averageIntervalDays(summary.getAverageIntervalDays())
            .refreshedAt(summary.getRefreshedAt())
            .build();
    }

    private List<CustomerProductSummaryResponse> listTopProducts(Long customerId, int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), 20);
        List<CustomerProductSummary> facts = customerProductSummaryRepository
            .findByCompanyIdAndCustomerIdOrderByTotalAmountDescTotalQuantityDesc(
                DEFAULT_COMPANY_ID,
                customerId,
                PageRequest.of(0, limit)
            );
        Map<Long, ProductSku> skus = productSkuRepository.findAllById(
                facts.stream().map(CustomerProductSummary::getProductSkuId).toList()
            ).stream()
            .collect(Collectors.toMap(ProductSku::getId, Function.identity()));

        return facts.stream().map(fact -> {
            ProductSku sku = skus.get(fact.getProductSkuId());
            return CustomerProductSummaryResponse.builder()
                .productSkuId(fact.getProductSkuId())
                .skuCode(sku == null ? null : sku.getSkuCode())
                .skuName(sku == null ? null : sku.getSkuName())
                .productName(sku == null || sku.getProduct() == null
                    ? null
                    : sku.getProduct().getProductName())
                .barcode(sku == null ? null : sku.getBarcode())
                .totalOrderCount(fact.getTotalOrderCount())
                .totalQuantity(fact.getTotalQuantity())
                .totalAmount(fact.getTotalAmount())
                .firstOrderDate(fact.getFirstOrderDate())
                .lastOrderDate(fact.getLastOrderDate())
                .averageIntervalDays(fact.getAverageIntervalDays())
                .build();
        }).toList();
    }

    private Map<Long, Customer> loadCustomers(Collection<Long> customerIds) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Customer> customers = new HashMap<>();
        customerRepository.findAllById(customerIds).stream()
            .filter(customer -> DEFAULT_COMPANY_ID.equals(customer.getCompanyId()))
            .forEach(customer -> customers.put(customer.getId(), customer));
        return customers;
    }

    private String normalizeCustomerSort(String sortBy) {
        Set<String> allowedFields = Set.of(
            "totalAmount",
            "totalOrderCount",
            "lastOrderDate",
            "averageIntervalDays",
            "refreshedAt"
        );
        return allowedFields.contains(sortBy) ? sortBy : "totalAmount";
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank()
            ? ""
            : keyword.trim().toLowerCase(Locale.ROOT);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "dateRange", "constraint", "startDate must not be after endDate")
            );
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 366) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "dateRange", "constraint", "range must not exceed 366 days")
            );
        }
    }

    private BigDecimal averageAmount(BigDecimal amount, Long count) {
        long safeCount = safeLong(count);
        if (amount == null || safeCount == 0) {
            return BigDecimal.ZERO;
        }
        return amount.divide(BigDecimal.valueOf(safeCount), 2, RoundingMode.HALF_UP);
    }

    private long sumStatus(
        List<SalesDailySummaryResponse> rows,
        Function<SalesDailySummaryResponse, Long> extractor
    ) {
        return rows.stream().map(extractor).mapToLong(this::safeLong).sum();
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private SalesDailySummaryResponse toSalesDailyResponse(SalesDailySummary summary) {
        return SalesDailySummaryResponse.builder()
            .summaryDate(summary.getSummaryDate())
            .totalOrderCount(summary.getTotalOrderCount())
            .totalAmount(summary.getTotalAmount())
            .draftCount(summary.getDraftCount())
            .pendingApprovalCount(summary.getPendingApprovalCount())
            .approvedAwaitingShipmentCount(summary.getApprovedAwaitingShipmentCount())
            .shippedCount(summary.getShippedCount())
            .rejectedCount(summary.getRejectedCount())
            .cancelledCount(summary.getCancelledCount())
            .voidedCount(summary.getVoidedCount())
            .refreshedAt(summary.getRefreshedAt())
            .build();
    }
}

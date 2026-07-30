package com.wms.system.service;

import com.wms.system.dto.report.CustomerFactSummaryResponse;
import com.wms.system.dto.report.SalesOverviewResponse;
import com.wms.system.entity.Customer;
import com.wms.system.entity.CustomerFactSummary;
import com.wms.system.entity.CustomerProductSummary;
import com.wms.system.entity.Product;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportQueryServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerFactSummaryRepository customerFactSummaryRepository;

    @Mock
    private CustomerProductSummaryRepository customerProductSummaryRepository;

    @Mock
    private ProductSkuRepository productSkuRepository;

    @Mock
    private SalesDailySummaryRepository salesDailySummaryRepository;

    @InjectMocks
    private ReportQueryService reportQueryService;

    @Test
    void customerDetailIncludesIdentityAndTopProductFacts() {
        Customer customer = customer(7L);
        CustomerFactSummary summary = CustomerFactSummary.builder()
            .customerId(7L)
            .totalOrderCount(4L)
            .totalAmount(new BigDecimal("800.00"))
            .lastOrderDate(LocalDate.of(2026, 7, 20))
            .averageIntervalDays(new BigDecimal("12.50"))
            .refreshedAt(LocalDateTime.of(2026, 7, 23, 3, 0))
            .build();
        CustomerProductSummary productFact = CustomerProductSummary.builder()
            .customerId(7L)
            .productSkuId(11L)
            .totalOrderCount(3L)
            .totalQuantity(24L)
            .totalAmount(new BigDecimal("600.00"))
            .averageIntervalDays(new BigDecimal("10.00"))
            .build();
        Product spu = Product.builder().id(3L).productCode("P000003").productName("Green Tea").build();
        ProductSku sku = ProductSku.builder()
            .id(11L)
            .skuCode("SKU0000011")
            .skuName("500g bag")
            .name("Green Tea 500g")
            .barcode("6900000000011")
            .product(spu)
            .unitPrice(BigDecimal.TEN)
            .build();

        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(customerFactSummaryRepository.findByCompanyIdAndCustomerId(1L, 7L))
            .thenReturn(Optional.of(summary));
        when(customerProductSummaryRepository
            .findByCompanyIdAndCustomerIdOrderByTotalAmountDescTotalQuantityDesc(eq(1L), eq(7L), any()))
            .thenReturn(List.of(productFact));
        when(productSkuRepository.findAllById(List.of(11L))).thenReturn(List.of(sku));

        CustomerFactSummaryResponse response = reportQueryService.getCustomerFactSummary(7L, 5);

        assertThat(response.getCustomerName()).isEqualTo("Acme Stores");
        assertThat(response.getAverageOrderValue()).isEqualByComparingTo("200.00");
        assertThat(response.getTopProducts()).singleElement().satisfies(topProduct -> {
            assertThat(topProduct.getSkuCode()).isEqualTo("SKU0000011");
            assertThat(topProduct.getProductName()).isEqualTo("Green Tea");
            assertThat(topProduct.getTotalQuantity()).isEqualTo(24L);
        });
    }

    @Test
    void customerWithoutOrdersReturnsZeroFacts() {
        Customer customer = customer(8L);
        when(customerRepository.findById(8L)).thenReturn(Optional.of(customer));
        when(customerFactSummaryRepository.findByCompanyIdAndCustomerId(1L, 8L))
            .thenReturn(Optional.empty());
        when(customerProductSummaryRepository
            .findByCompanyIdAndCustomerIdOrderByTotalAmountDescTotalQuantityDesc(eq(1L), eq(8L), any()))
            .thenReturn(List.of());

        CustomerFactSummaryResponse response = reportQueryService.getCustomerFactSummary(8L, 5);

        assertThat(response.getTotalOrderCount()).isZero();
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTopProducts()).isEmpty();
    }

    @Test
    void salesOverviewAggregatesEffectiveSalesAndWorkflowStatuses() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 2);
        SalesDailySummary first = daily(start, 3L, "300.00", 1L, 1L, 0L, 1L, 1L, 0L, 0L);
        SalesDailySummary second = daily(end, 2L, "250.00", 0L, 0L, 1L, 1L, 0L, 1L, 1L);
        when(salesDailySummaryRepository.findByCompanyIdAndSummaryDateBetweenOrderBySummaryDateAsc(1L, start, end))
            .thenReturn(List.of(first, second));

        SalesOverviewResponse response = reportQueryService.getSalesOverview(start, end);

        assertThat(response.getTotalOrderCount()).isEqualTo(5L);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("550.00");
        assertThat(response.getAverageOrderValue()).isEqualByComparingTo("110.00");
        assertThat(response.getShippedCount()).isEqualTo(2L);
        assertThat(response.getRejectedCount()).isEqualTo(1L);
        assertThat(response.getCancelledCount()).isEqualTo(1L);
        assertThat(response.getVoidedCount()).isEqualTo(1L);
    }

    @Test
    void salesReportRejectsDateRangesOverOneYear() {
        assertThatThrownBy(() -> reportQueryService.listSalesDailySummary(
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2026, 7, 1)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorKey()).isEqualTo(ErrorKeys.VALIDATION_FAILED)
        );
    }

    private Customer customer(Long id) {
        return Customer.builder()
            .id(id)
            .code("CLIENT-" + id)
            .name("Acme Stores")
            .customerType(CustomerType.CLIENT)
            .source(CustomerSource.MANUAL)
            .build();
    }

    private SalesDailySummary daily(
        LocalDate date,
        Long orders,
        String amount,
        Long draft,
        Long pending,
        Long approved,
        Long shipped,
        Long rejected,
        Long cancelled,
        Long voided
    ) {
        return SalesDailySummary.builder()
            .summaryDate(date)
            .totalOrderCount(orders)
            .totalAmount(new BigDecimal(amount))
            .draftCount(draft)
            .pendingApprovalCount(pending)
            .approvedAwaitingShipmentCount(approved)
            .shippedCount(shipped)
            .rejectedCount(rejected)
            .cancelledCount(cancelled)
            .voidedCount(voided)
            .refreshedAt(date.atTime(3, 0))
            .build();
    }
}

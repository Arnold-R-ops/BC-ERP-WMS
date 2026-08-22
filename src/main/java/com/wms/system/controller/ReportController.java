package com.wms.system.controller;

import com.wms.system.dto.report.CustomerFactSummaryResponse;
import com.wms.system.dto.report.CustomerFactListItemResponse;
import com.wms.system.dto.report.SalesDailySummaryResponse;
import com.wms.system.dto.report.SalesOverviewResponse;
import com.wms.system.entity.enums.CustomerSource;
import com.wms.system.entity.enums.CustomerType;
import com.wms.system.service.ReportQueryService;
import com.wms.system.service.ReportSummaryRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportQueryService reportQueryService;
    private final ReportSummaryRefreshService reportSummaryRefreshService;

    @PostMapping("/refresh")
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    public ResponseEntity<ReportSummaryRefreshService.RefreshResult> refreshReportFacts() {
        return ResponseEntity.ok(reportSummaryRefreshService.refreshAll());
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyAuthority('global:view', 'customer:view', 'sales:view', 'TENANT_ADMIN')")
    public ResponseEntity<CustomerFactSummaryResponse> getCustomerFactSummary(
        @PathVariable("customerId") Long customerId,
        @RequestParam(name = "topProducts", defaultValue = "5") int topProducts
    ) {
        return ResponseEntity.ok(reportQueryService.getCustomerFactSummary(customerId, topProducts));
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAnyAuthority('global:view', 'customer:view', 'sales:view', 'TENANT_ADMIN')")
    public ResponseEntity<Page<CustomerFactListItemResponse>> listCustomerFacts(
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "customerType", required = false) CustomerType customerType,
        @RequestParam(name = "source", required = false) CustomerSource source,
        @RequestParam(name = "sortBy", defaultValue = "totalAmount") String sortBy,
        @RequestParam(name = "sortDirection", defaultValue = "desc") String sortDirection,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(reportQueryService.listCustomerFacts(
            keyword,
            customerType,
            source,
            sortBy,
            sortDirection,
            page,
            size
        ));
    }

    @GetMapping("/sales/overview")
    @PreAuthorize("hasAnyAuthority('global:view', 'sales:view', 'TENANT_ADMIN')")
    public ResponseEntity<SalesOverviewResponse> getSalesOverview(
        @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(reportQueryService.getSalesOverview(startDate, endDate));
    }

    @GetMapping("/sales/daily")
    @PreAuthorize("hasAnyAuthority('global:view', 'sales:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<SalesDailySummaryResponse>> listSalesDailySummary(
        @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(reportQueryService.listSalesDailySummary(startDate, endDate));
    }
}

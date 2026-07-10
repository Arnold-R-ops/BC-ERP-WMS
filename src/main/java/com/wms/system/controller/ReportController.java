package com.wms.system.controller;

import com.wms.system.dto.report.CustomerFactSummaryResponse;
import com.wms.system.dto.report.SalesDailySummaryResponse;
import com.wms.system.service.ReportQueryService;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyAuthority('customer:view', 'sales:view', 'SUPER_ADMIN')")
    public ResponseEntity<CustomerFactSummaryResponse> getCustomerFactSummary(
        @PathVariable("customerId") Long customerId
    ) {
        return ResponseEntity.ok(reportQueryService.getCustomerFactSummary(customerId));
    }

    @GetMapping("/sales/daily")
    @PreAuthorize("hasAnyAuthority('sales:view', 'SUPER_ADMIN')")
    public ResponseEntity<List<SalesDailySummaryResponse>> listSalesDailySummary(
        @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(reportQueryService.listSalesDailySummary(startDate, endDate));
    }
}

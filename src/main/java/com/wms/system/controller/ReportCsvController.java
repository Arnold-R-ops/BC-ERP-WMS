package com.wms.system.controller;

import com.wms.system.service.ReportCsvService;
import com.wms.system.entity.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;

@RestController @RequestMapping("/api/reports") @RequiredArgsConstructor
public class ReportCsvController {
    private final ReportCsvService csv;

    private ResponseEntity<byte[]> file(String name, String body) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(body.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/sales/export")
    @PreAuthorize("hasAnyAuthority('global:view', 'sales:view', 'TENANT_ADMIN')")
    public ResponseEntity<byte[]> sales(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return file("sales.csv", csv.sales(startDate, endDate));
    }

    @GetMapping("/customers/export")
    @PreAuthorize("hasAnyAuthority('global:view', 'customer:view', 'sales:view', 'TENANT_ADMIN')")
    public ResponseEntity<byte[]> customers(@RequestParam(required = false) String keyword,
        @RequestParam(required = false) CustomerType customerType, @RequestParam(required = false) CustomerSource source,
        @RequestParam(defaultValue = "totalAmount") String sortBy, @RequestParam(defaultValue = "desc") String sortDirection) {
        return file("customers.csv", csv.customers(keyword, customerType, source, sortBy, sortDirection));
    }

    @GetMapping("/customer/{id}/export")
    @PreAuthorize("hasAnyAuthority('global:view', 'customer:view', 'sales:view', 'TENANT_ADMIN')")
    public ResponseEntity<byte[]> products(@PathVariable Long id, @RequestParam(defaultValue = "8") int topProducts) {
        return file("customer-products.csv", csv.customerProducts(id, topProducts));
    }
}

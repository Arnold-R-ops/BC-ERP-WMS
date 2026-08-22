package com.wms.system.platform.controller;

import com.wms.system.platform.dto.*;
import com.wms.system.platform.service.PlatformCompanyDataService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import com.wms.system.tenant.model.TenantStatus;

@RestController
@RequestMapping("/api/platform/companies")
@RequiredArgsConstructor
public class PlatformCompanyDataController {
    private final PlatformCompanyDataService service;

    @GetMapping
    public Page<PlatformCompanyResponse> companies(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) TenantStatus status,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    HttpServletRequest request) {
        return service.listCompanies(keyword, status, page, size, request);
    }

    @GetMapping("/{companyId}")
    public PlatformCompanyResponse company(@PathVariable Long companyId, HttpServletRequest request) {
        return service.company(companyId, request);
    }

    @GetMapping("/{companyId}/data/{resource}")
    public PlatformDataPage data(@PathVariable Long companyId, @PathVariable String resource,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 HttpServletRequest request) {
        return service.read(companyId, resource, page, size, request);
    }
}

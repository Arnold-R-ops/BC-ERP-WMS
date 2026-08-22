package com.wms.system.platform.controller;

import com.wms.system.platform.dto.PlatformExportJobResponse;
import com.wms.system.platform.service.PlatformExportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/companies/{companyId}/exports")
@RequiredArgsConstructor
public class PlatformExportController {
    private final PlatformExportService service;

    @PostMapping
    public PlatformExportJobResponse create(@PathVariable Long companyId,
                                             @Valid @RequestBody ExportRequest body,
                                             HttpServletRequest request) {
        return service.create(companyId, body.resource(), request);
    }

    @GetMapping("/{jobId}")
    public PlatformExportJobResponse status(@PathVariable Long companyId, @PathVariable String jobId) {
        PlatformExportJobResponse response = service.status(jobId);
        if (!companyId.equals(response.companyId())) throw new java.util.NoSuchElementException("Export job not found");
        return response;
    }

    @GetMapping("/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long companyId, @PathVariable String jobId,
                                              HttpServletRequest request) {
        PlatformExportJobResponse response = service.status(jobId);
        if (!companyId.equals(response.companyId())) throw new java.util.NoSuchElementException("Export job not found");
        return service.download(jobId, request);
    }

    public record ExportRequest(@Pattern(regexp = "ALL|[a-z_]{2,80}") String resource) {}
}

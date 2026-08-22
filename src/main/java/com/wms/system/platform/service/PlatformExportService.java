package com.wms.system.platform.service;

import com.wms.system.platform.config.PlatformAccessProperties;
import com.wms.system.platform.dto.PlatformExportJobResponse;
import com.wms.system.platform.model.PlatformExportJob;
import com.wms.system.platform.repository.PlatformExportJobRepository;
import com.wms.system.security.PlatformSecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.Executor;

@Service
public class PlatformExportService {
    private static final Set<String> FIRST_RELEASE_DATASETS = Set.of(
        "users", "roles", "warehouses", "products", "inventory", "sales_orders"
    );
    private final PlatformExportJobRepository repository;
    private final PlatformCompanyDataService companyDataService;
    private final PlatformAccessGuard guard;
    private final PlatformAuditService auditService;
    private final PlatformExportWorker worker;
    private final Executor executor;
    private final PlatformAccessProperties properties;

    public PlatformExportService(PlatformExportJobRepository repository,
            PlatformCompanyDataService companyDataService, PlatformAccessGuard guard,
            PlatformAuditService auditService, PlatformExportWorker worker,
            @Qualifier("platformExportExecutor") Executor executor,
            PlatformAccessProperties properties) {
        this.repository = repository; this.companyDataService = companyDataService;
        this.guard = guard; this.auditService = auditService; this.worker = worker;
        this.executor = executor; this.properties = properties;
    }

    public PlatformExportJobResponse create(Long companyId, String resource, HttpServletRequest request) {
        String normalized = resource == null ? "" : resource.trim();
        if (!FIRST_RELEASE_DATASETS.contains(normalized)) {
            throw new IllegalArgumentException("A single approved platform dataset is required");
        }
        PlatformSecurityUser actor = guard.requireExport(companyId, normalized);
        companyDataService.requireAvailable(companyId);
        PlatformExportJob job = repository.save(PlatformExportJob.builder()
            .publicId(UUID.randomUUID().toString()).platformUserId(actor.getId())
            .targetTenantId(companyId).status("PENDING").requestedResource(normalized)
            .expiresAt(OffsetDateTime.now().plusHours(properties.getExportRetentionHours())).build());
        auditService.record(actor.getId(), companyId, "EXPORT", normalized, "REQUESTED",
            Map.of("jobId", job.getPublicId()), request);
        executor.execute(() -> worker.run(job.getId()));
        return PlatformExportJobResponse.from(job);
    }

    static Set<String> firstReleaseDatasets() {
        return FIRST_RELEASE_DATASETS;
    }

    @Transactional(readOnly = true)
    public PlatformExportJobResponse status(String publicId) {
        PlatformSecurityUser actor = guard.requireTenantDirectory();
        PlatformExportJob job = repository.findByPublicIdAndPlatformUserId(publicId, actor.getId())
            .orElseThrow(() -> new NoSuchElementException("Export job not found"));
        guard.requireExport(job.getTargetTenantId(), job.getRequestedResource());
        return PlatformExportJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(String publicId, HttpServletRequest request) {
        PlatformSecurityUser actor = guard.requireTenantDirectory();
        PlatformExportJob job = repository.findByPublicIdAndPlatformUserId(publicId, actor.getId())
            .filter(candidate -> "COMPLETED".equals(candidate.getStatus()))
            .filter(candidate -> candidate.getExpiresAt().isAfter(OffsetDateTime.now()))
            .orElseThrow(() -> new NoSuchElementException("Export file unavailable"));
        guard.requireExport(job.getTargetTenantId(), job.getRequestedResource());
        Path root = properties.getExportDirectory().toAbsolutePath().normalize();
        Path path = Path.of(job.getFilePath()).toAbsolutePath().normalize();
        if (!path.getParent().equals(root) || !Files.isRegularFile(path))
            throw new NoSuchElementException("Export file unavailable");
        auditService.record(actor.getId(), job.getTargetTenantId(), "EXPORT", job.getRequestedResource(),
            "DOWNLOADED", Map.of("jobId", job.getPublicId(), "sha256", job.getFileSha256()), request);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=company-export-" + publicId + ".zip")
            .body(new FileSystemResource(path));
    }
}

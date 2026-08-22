package com.wms.system.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.platform.config.PlatformAccessProperties;
import com.wms.system.platform.model.PlatformExportJob;
import com.wms.system.platform.repository.PlatformExportJobRepository;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.zip.*;

@Service
@RequiredArgsConstructor
public class PlatformExportWorker {
    private final PlatformExportJobRepository jobRepository;
    private final TenantRepository tenantRepository;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;
    private final PlatformAccessProperties properties;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;

    public void run(Long jobId) {
        PlatformExportJob seed = transaction().execute(status -> jobRepository.findById(jobId).orElseThrow());
        if (seed == null) return;
        try {
            transaction().executeWithoutResult(status -> {
                PlatformExportJob job = jobRepository.findById(jobId).orElseThrow();
                job.setStatus("RUNNING");
                job.setStartedAt(OffsetDateTime.now());
            });
            Tenant tenant = transaction().execute(status -> tenantRepository.findById(seed.getTargetTenantId())
                .filter(candidate -> candidate.getStatus() != com.wms.system.tenant.model.TenantStatus.PURGED)
                .orElseThrow());
            if (tenant == null) throw new IllegalStateException("Company data unavailable");
            ExportResult result = TenantContextHolder.runWithResolvedTenant(
                PlatformCompanyDataService.context(tenant), () -> export(seed, tenant));
            transaction().executeWithoutResult(status -> {
                PlatformExportJob job = jobRepository.findById(jobId).orElseThrow();
                job.setStatus("COMPLETED");
                job.setFilePath(result.path().toString());
                job.setFileSha256(result.sha256());
                job.setRecordCount(result.recordCount());
                job.setCompletedAt(OffsetDateTime.now());
            });
            auditService.recordBackground(seed.getPlatformUserId(), seed.getTargetTenantId(), "EXPORT",
                seed.getRequestedResource(), "SUCCESS", Map.of("jobId", seed.getPublicId(),
                    "recordCount", result.recordCount(), "sha256", result.sha256()));
        } catch (Exception exception) {
            transaction().executeWithoutResult(status -> jobRepository.findById(jobId).ifPresent(job -> {
                job.setStatus("FAILED");
                job.setErrorCode("PLATFORM_EXPORT_FAILED");
                job.setCompletedAt(OffsetDateTime.now());
            }));
            auditService.recordBackground(seed.getPlatformUserId(), seed.getTargetTenantId(), "EXPORT",
                seed.getRequestedResource(), "FAILED", Map.of("jobId", seed.getPublicId(),
                    "error", exception.getClass().getSimpleName()));
        }
    }

    private ExportResult export(PlatformExportJob job, Tenant tenant) {
        Path root = properties.getExportDirectory().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            Path target = root.resolve(job.getPublicId() + ".zip").normalize();
            if (!target.getParent().equals(root)) throw new SecurityException("Invalid export path");
            Collection<PlatformDatasetCatalog.Dataset> datasets = "ALL".equals(job.getRequestedResource())
                ? PlatformDatasetCatalog.all()
                : List.of(PlatformDatasetCatalog.find(job.getRequestedResource()).orElseThrow());
            long[] total = {0L};
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target,
                    StandardOpenOption.CREATE_NEW), StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(objectMapper.writeValueAsBytes(Map.of(
                    "companyId", tenant.getId(), "companyName", tenant.getDisplayName(),
                    "createdAt", OffsetDateTime.now().toString(),
                    "datasets", datasets.stream().map(PlatformDatasetCatalog.Dataset::code).toList())));
                zip.closeEntry();
                TransactionTemplate transaction = transaction();
                transaction.executeWithoutResult(status -> datasets.forEach(dataset -> {
                    try {
                        zip.putNextEntry(new ZipEntry(dataset.code() + ".csv"));
                        zip.write(0xEF); zip.write(0xBB); zip.write(0xBF);
                        writeCsvRow(zip, dataset.headers().toArray());
                        int offset = 0;
                        while (true) {
                            List<Object[]> rows = entityManager.createQuery(dataset.selectJpql(), Object[].class)
                                .setFirstResult(offset).setMaxResults(500).getResultList();
                            for (Object[] row : rows) writeCsvRow(zip, row);
                            total[0] += rows.size();
                            if (rows.size() < 500) break;
                            offset += rows.size();
                        }
                        zip.closeEntry();
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                }));
            }
            return new ExportResult(target, sha256(target), total[0]);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void writeCsvRow(OutputStream output, Object[] values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) output.write(',');
            String value = values[i] == null ? "" : String.valueOf(values[i]);
            if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
            value = value.replace("\"", "\"\"");
            output.write(('"' + value + '"').getBytes(StandardCharsets.UTF_8));
        }
        output.write('\n');
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) { input.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), digest)); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private TransactionTemplate transaction() { return new TransactionTemplate(transactionManager); }
    private record ExportResult(Path path, String sha256, long recordCount) {}
    private static final class DigestOutputStream extends java.security.DigestOutputStream {
        DigestOutputStream(OutputStream stream, MessageDigest digest) { super(stream, digest); }
    }
}

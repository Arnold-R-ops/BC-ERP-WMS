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
    private final com.wms.system.tenant.config.TenancyProperties tenancyProperties;
    private final PlatformExportJobRepository jobRepository;
    private final TenantRepository tenantRepository;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;
    private final PlatformAccessProperties properties;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;
    private final com.wms.system.tenant.security.PlatformTenantDataSafety safety;

    public void run(Long jobId) {
        PlatformExportJob seed = transaction().execute(status -> {
            var job=jobRepository.findForUpdate(jobId).orElseThrow();
            if(!"PENDING".equals(job.getStatus())) return null;
            job.setStatus("RUNNING"); job.setStartedAt(OffsetDateTime.now());
            return job;
        });
        if (seed == null) return;
        ExportResult result = null;
        try {
            if(!PlatformExportService.firstReleaseDatasets().contains(seed.getRequestedResource())) throw new IllegalArgumentException("Unapproved export dataset");
            safety.requireWorkerExport(seed.getPlatformUserId(),seed.getTargetTenantId(),seed.getRequestedResource(),seed.getAccessReason());
            transaction().executeWithoutResult(status -> {
                PlatformExportJob job = jobRepository.findById(jobId).orElseThrow();
                job.setStatus("RUNNING");
                job.setStartedAt(OffsetDateTime.now());
            });
            Tenant tenant = transaction().execute(status -> tenantRepository.findById(seed.getTargetTenantId())
                .filter(candidate -> candidate.getStatus() != com.wms.system.tenant.model.TenantStatus.PURGED)
                .orElseThrow());
            if (tenant == null) throw new IllegalStateException("Company data unavailable");
            try(var admission=com.wms.system.tenant.security.TenantExecutionScope.open(tenant.getId(),
                    com.wms.system.tenant.security.TenantOperationPurpose.PLATFORM_READ,null)) {
                result = TenantContextHolder.runWithResolvedTenant(
                    PlatformCompanyDataService.context(tenant, tenancyProperties.getBaseDomain()), () -> export(seed, tenant));
            }
            safety.requireWorkerExport(seed.getPlatformUserId(),seed.getTargetTenantId(),seed.getRequestedResource(),seed.getAccessReason());
            var completedSafety=safety.auditDecodedContext(seed.getTargetTenantId(),seed.getAccessReason());
            ExportResult completed=result;
            transaction().executeWithoutResult(status -> {
                PlatformExportJob job = jobRepository.findById(jobId).orElseThrow();
                job.setStatus("COMPLETED");
                job.setFilePath(completed.path().toString());
                job.setFileSha256(completed.sha256());
                job.setRecordCount(completed.recordCount());
                job.setCompletedAt(OffsetDateTime.now());
            });
            auditService.recordBackground(seed.getPlatformUserId(), seed.getTargetTenantId(), "EXPORT",
                seed.getRequestedResource(), "SUCCESS", Map.of("jobId", seed.getPublicId(),
                    "recordCount", result.recordCount(), "sha256", result.sha256(), "safety", completedSafety));
        } catch (Exception exception) {
            if(result!=null) discardOwnArtifact(result.path());
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
        Path target = root.resolve(job.getPublicId() + ".zip").normalize();
        boolean created=false;
        try {
            Files.createDirectories(root);
            if (!target.getParent().equals(root)) throw new SecurityException("Invalid export path");
            Collection<PlatformDatasetCatalog.Dataset> datasets = "ALL".equals(job.getRequestedResource())
                ? PlatformDatasetCatalog.all()
                : List.of(PlatformDatasetCatalog.find(job.getRequestedResource()).orElseThrow());
            long[] total = {0L};
            OutputStream output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW);
            created=true;
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(objectMapper.writeValueAsBytes(Map.of(
                    "companyId", tenant.getId(), "companyName", tenant.getDisplayName(),
                    "createdAt", OffsetDateTime.now().toString(),
                    "datasets", datasets.stream().map(PlatformDatasetCatalog.Dataset::code).toList())));
                zip.closeEntry();
                TransactionTemplate transaction = transaction();
                transaction.setReadOnly(true);
                transaction.executeWithoutResult(status -> datasets.forEach(dataset -> {
                    try {
                        zip.putNextEntry(new ZipEntry(dataset.code() + ".csv"));
                        zip.write(0xEF); zip.write(0xBB); zip.write(0xBF);
                        writeCsvRow(zip, dataset.headers().toArray());
                        int offset = 0;
                        while (true) {
                            safety.requireWorkerExport(job.getPlatformUserId(),job.getTargetTenantId(),job.getRequestedResource(),job.getAccessReason());
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
            if(created) discardOwnArtifact(target);
            throw new UncheckedIOException(exception);
        } catch(RuntimeException exception) {
            if(created) discardOwnArtifact(target);
            throw exception;
        }
    }

    private void discardOwnArtifact(Path target) {
        Path root=properties.getExportDirectory().toAbsolutePath().normalize();
        if(!target.toAbsolutePath().normalize().getParent().equals(root)) throw new SecurityException("Invalid export cleanup path");
        try { Files.deleteIfExists(target); }
        catch(IOException failure) { org.slf4j.LoggerFactory.getLogger(getClass()).error("Failed to remove incomplete platform export artifact: {}",target.getFileName()); }
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

package com.wms.system.platform.service;

import com.wms.system.entity.Warehouse;
import com.wms.system.platform.model.*;
import com.wms.system.platform.config.PlatformAccessProperties;
import com.wms.system.platform.repository.*;
import com.wms.system.repository.WarehouseRepository;
import com.wms.system.security.PlatformSecurityUser;
import com.wms.system.tenant.context.*;
import com.wms.system.tenant.model.*;
import com.wms.system.tenant.repository.TenantRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.List;
import java.nio.file.*;
import java.util.zip.ZipFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PlatformCompanyDataServiceIntegrationTest {
    @Autowired PlatformCompanyDataService service;
    @Autowired TenantRepository tenantRepository;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired PlatformUserRepository platformUserRepository;
    @Autowired PlatformAuditLogRepository auditRepository;
    @Autowired PlatformExportService exportService;
    @Autowired PlatformExportJobRepository exportJobRepository;
    @Autowired PlatformAccessProperties accessProperties;
    @Autowired PlatformAccessGrantRepository accessGrantRepository;
    @Autowired PlatformTransactionManager transactionManager;
    Long actorId;

    @BeforeEach void setUp() {
        PlatformUser actor = platformUserRepository.save(PlatformUser.builder()
            .normalizedEmail("reader-" + java.util.UUID.randomUUID() + "@example.com")
            .passwordHash("hidden").displayName("Reader").enabled(true).build());
        actorId = actor.getId();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformSecurityUser(actor), null,
            List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_TENANT_READ"))));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); TenantContextHolder.clear(); }

    @Test void platformReadUsesTargetCompanyRlsAndAuditStaysPlatformOnly() {
        Tenant alpha = tenant("alpha-" + suffix());
        Tenant beta = tenant("beta-" + suffix());
        grant(alpha, "READ", "warehouses");
        grant(beta, "READ", "warehouses");
        inCompany(alpha, () -> saveWarehouse("SAME", "Alpha only"));
        inCompany(beta, () -> saveWarehouse("SAME", "Beta only"));

        var alphaPage = service.read(alpha.getId(), "warehouses", 0, 20,
            new MockHttpServletRequest());
        var betaPage = service.read(beta.getId(), "warehouses", 0, 20,
            new MockHttpServletRequest());

        assertThat(alphaPage.content()).extracting(row -> row.get("name")).containsExactly("Alpha only");
        assertThat(betaPage.content()).extracting(row -> row.get("name")).containsExactly("Beta only");
        assertThat(auditRepository.findAll()).filteredOn(log -> log.getPlatformUserId().equals(actorId))
            .extracting(PlatformAuditLog::getTargetTenantId).contains(alpha.getId(), beta.getId());
        assertThat(PlatformDatasetCatalog.find("platform_audit_logs")).isEmpty();
    }

    @Test void tenantDirectoryWritesOneAggregateAuditRecordPerPage() {
        authenticate("ROLE_PLATFORM_SUPER_ADMIN");
        tenant("directory-a-" + suffix());
        tenant("directory-b-" + suffix());

        service.listCompanies(0, 20, new MockHttpServletRequest());

        var directoryAudits = auditRepository.findAll().stream()
            .filter(log -> log.getPlatformUserId().equals(actorId))
            .filter(log -> "tenant_directory".equals(log.getResourceType()))
            .toList();
        assertThat(directoryAudits).hasSize(1);
        assertThat(directoryAudits.get(0).getTargetTenantId()).isNull();
        assertThat(directoryAudits.get(0).getDetailJson())
            .contains("\"page\":0", "\"size\":20", "\"returned\"");
    }

    @Test void tenantDirectoryFiltersInDatabaseAndExcludesPurgedTenants() {
        authenticate("ROLE_PLATFORM_SUPER_ADMIN");
        String marker = "filter-" + suffix();
        Tenant active = tenant(marker + "-active", TenantStatus.ACTIVE);
        tenant(marker + "-suspended", TenantStatus.SUSPENDED);
        tenant(marker + "-purged", TenantStatus.PURGED);

        var result = service.listCompanies(marker, TenantStatus.ACTIVE, 0, 20,
            new MockHttpServletRequest());

        assertThat(result.getContent()).extracting(com.wms.system.platform.dto.PlatformCompanyResponse::id)
            .containsExactly(active.getId());
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test void delegatedDirectorySearchStaysInsideActiveGrantScope() {
        String marker = "delegated-" + suffix();
        Tenant granted = tenant(marker + "-granted");
        tenant(marker + "-outside");
        grant(granted, "READ", "users");

        var result = service.listCompanies(marker, null, 0, 20, new MockHttpServletRequest());

        assertThat(result.getContent()).extracting(com.wms.system.platform.dto.PlatformCompanyResponse::id)
            .containsExactly(granted.getId());
    }

    @Test void tenantOverviewIsExplicitlyAuditedAndPurgedTenantFailureIsRecorded() {
        authenticate("ROLE_PLATFORM_SUPER_ADMIN");
        Tenant active = tenant("overview-active-" + suffix());
        Tenant purged = tenant("overview-purged-" + suffix(), TenantStatus.PURGED);
        Tenant delegatedAnchor = tenant("overview-anchor-" + suffix());
        Tenant outsideGrant = tenant("overview-outside-" + suffix());

        var overview = service.company(active.getId(), new MockHttpServletRequest());
        assertThat(overview.id()).isEqualTo(active.getId());
        assertThatThrownBy(() -> service.company(purged.getId(), new MockHttpServletRequest()))
            .isInstanceOf(java.util.NoSuchElementException.class);
        grant(delegatedAnchor, "READ", "users");
        authenticate("ROLE_PLATFORM_TENANT_READ");
        assertThatThrownBy(() -> service.company(outsideGrant.getId(), new MockHttpServletRequest()))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        var overviewAudits = auditRepository.findAll().stream()
            .filter(log -> log.getPlatformUserId().equals(actorId))
            .filter(log -> "tenant_overview".equals(log.getResourceType()))
            .toList();
        assertThat(overviewAudits).filteredOn(log -> log.getTargetTenantId().equals(active.getId()))
            .extracting(PlatformAuditLog::getResult).containsExactly("SUCCESS");
        assertThat(overviewAudits).filteredOn(log -> log.getTargetTenantId().equals(purged.getId()))
            .extracting(PlatformAuditLog::getResult).containsExactly("FAILED");
        assertThat(overviewAudits).filteredOn(log -> log.getTargetTenantId().equals(outsideGrant.getId()))
            .extracting(PlatformAuditLog::getResult).containsExactly("FAILED");
    }

    @Test void singleDatasetExportContainsNoSecretDataset() throws Exception {
        Tenant company = tenant("export-" + suffix());
        inCompany(company, () -> saveWarehouse("EXPORT", "Export warehouse"));
        grant(company, "EXPORT", "warehouses");
        authenticate("ROLE_PLATFORM_TENANT_EXPORT");
        Path exportRoot = Files.createTempDirectory("bcwms-platform-export-test-");
        accessProperties.setExportDirectory(exportRoot);
        var created = exportService.create(company.getId(), "warehouses", new MockHttpServletRequest());
        PlatformExportJob job = null;
        for (int attempt = 0; attempt < 100; attempt++) {
            job = exportJobRepository.findByPublicIdAndPlatformUserId(created.id(), actorId).orElseThrow();
            if ("COMPLETED".equals(job.getStatus()) || "FAILED".equals(job.getStatus())) break;
            Thread.sleep(50);
        }
        assertThat(job).isNotNull();
        assertThat(job.getStatus()).isEqualTo("COMPLETED");
        assertThat(job.getFileSha256()).hasSize(64);
        try (ZipFile zip = new ZipFile(job.getFilePath(), java.nio.charset.StandardCharsets.UTF_8)) {
            List<String> names = zip.stream().map(java.util.zip.ZipEntry::getName).toList();
            assertThat(names).containsExactlyInAnyOrder("manifest.json", "warehouses.csv");
            assertThat(names).noneMatch(name -> name.contains("audit") || name.contains("password")
                || name.contains("token") || name.contains("secret"));
        }
        Files.deleteIfExists(Path.of(job.getFilePath()));
        Files.deleteIfExists(exportRoot);
    }

    private Tenant tenant(String slug) {
        return tenant(slug, TenantStatus.ACTIVE);
    }
    private Tenant tenant(String slug, TenantStatus status) {
        return tenantRepository.save(Tenant.builder().tenantCode("T-" + slug).displayName(slug)
            .slug(slug).status(status).build());
    }
    private void grant(Tenant tenant, String capability, String dataset) {
        accessGrantRepository.save(PlatformAccessGrant.builder()
            .granteePlatformUserId(actorId).grantedByPlatformUserId(actorId)
            .capability(capability).tenantId(tenant.getId()).datasetCode(dataset)
            .effectiveFrom(java.time.OffsetDateTime.now().minusMinutes(1))
            .expiresAt(java.time.OffsetDateTime.now().plusDays(1)).build());
    }
    private String suffix() { return java.util.UUID.randomUUID().toString().substring(0, 6); }
    private void saveWarehouse(String code, String name) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            warehouseRepository.saveAndFlush(Warehouse.builder().code(code).name(name).isActive(true).build()));
    }
    private void authenticate(String authority) {
        PlatformUser actor = platformUserRepository.findById(actorId).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformSecurityUser(actor), null, List.of(new SimpleGrantedAuthority(authority))));
    }
    private void inCompany(Tenant tenant, Runnable action) {
        TenantContextHolder.runWithTenant(PlatformCompanyDataService.context(tenant), action);
    }
}

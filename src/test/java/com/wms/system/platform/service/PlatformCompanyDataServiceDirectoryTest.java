package com.wms.system.platform.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.platform.config.PlatformAccessProperties;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformTenantDirectoryRepository;
import com.wms.system.security.PlatformSecurityUser;
import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.model.TenantStatus;
import com.wms.system.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformCompanyDataServiceDirectoryTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final PlatformTenantDirectoryRepository delegatedDirectory = mock(PlatformTenantDirectoryRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final PlatformAccessGuard guard = mock(PlatformAccessGuard.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final PlatformAccessProperties properties = new PlatformAccessProperties();
    private PlatformCompanyDataService service;
    private PlatformSecurityUser actor;

    @BeforeEach
    void setUp() {
        properties.setMaxPageSize(100);
        service = new PlatformCompanyDataService(tenants, delegatedDirectory, entityManager,
            transactionManager, properties, guard, audit);
        actor = new PlatformSecurityUser(PlatformUser.builder().id(7L)
            .normalizedEmail("platform@example.com").passwordHash("hidden")
            .displayName("Platform").enabled(true).build());
        when(guard.requireTenantDirectory()).thenReturn(actor);
    }

    @Test
    void administratorWithAllTenantDirectoryScopeUsesFilteredDatabasePageAndSafeAuditSummary() {
        when(guard.hasAllTenantDirectoryAccess()).thenReturn(true);
        Tenant tenant = Tenant.builder().id(11L).tenantCode("EAST-01").displayName("华东")
            .slug("east-01").status(TenantStatus.ACTIVE).build();
        when(tenants.searchPlatformDirectory(eq("华东"), eq(true), eq(TenantStatus.ACTIVE),
            eq(TenantStatus.PURGED), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(tenant)));

        var result = service.listCompanies("  华东  ", TenantStatus.ACTIVE, -2, 999,
            new MockHttpServletRequest());

        assertThat(result.getContent()).hasSize(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(tenants).searchPlatformDirectory(eq("华东"), eq(true), eq(TenantStatus.ACTIVE),
            eq(TenantStatus.PURGED), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detail = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq(7L), isNull(), eq("READ"), eq("tenant_directory"),
            eq("SUCCESS"), detail.capture(), any());
        assertThat(detail.getValue()).containsEntry("keywordApplied", true)
            .containsEntry("keywordLength", 2).containsEntry("status", "ACTIVE");
        assertThat(detail.getValue().values()).doesNotContain("华东");
    }

    @Test
    void delegatedAdministratorUsesActiveGrantJoinInsteadOfAnInMemoryTenantList() {
        when(guard.hasAllTenantDirectoryAccess()).thenReturn(false);
        when(guard.hasAuthority("ROLE_PLATFORM_TENANT_READ")).thenReturn(true);
        when(delegatedDirectory.searchAuthorized(eq(7L), eq(List.of("READ")), any(),
            eq(""), eq(false), isNull(), eq(TenantStatus.PURGED), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        service.listCompanies(" ", null, 0, 20, new MockHttpServletRequest());

        verify(delegatedDirectory).searchAuthorized(eq(7L), eq(List.of("READ")), any(),
            eq(""), eq(false), isNull(), eq(TenantStatus.PURGED), any(Pageable.class));
        verify(tenants, never()).findAll(any(Pageable.class));
        verify(tenants, never()).findAllById(any());
    }

    @Test
    void purgedStatusAndOversizedKeywordsAreRejectedBeforeQuerying() {
        assertThatThrownBy(() -> service.listCompanies(null, TenantStatus.PURGED, 0, 20,
            new MockHttpServletRequest())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.listCompanies("x".repeat(161), null, 0, 20,
            new MockHttpServletRequest())).isInstanceOf(BusinessException.class);
        verifyNoInteractions(tenants, delegatedDirectory, audit);
    }
}

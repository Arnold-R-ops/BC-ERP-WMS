package com.wms.system.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.platform.model.PlatformAuditLog;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformAuditLogRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.platform.repository.PlatformAccessGrantRepository;
import com.wms.system.security.PlatformSecurityUser;
import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAuditQueryServiceTest {
    @Mock PlatformAuditLogRepository auditRepository;
    @Mock PlatformUserRepository userRepository;
    @Mock PlatformAccessGrantRepository grantRepository;
    @Mock TenantRepository tenantRepository;
    @Mock PlatformAuditService auditService;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void returnsOnlyWhitelistedAuditSummaryFields() {
        PlatformUser actor = PlatformUser.builder().id(7L).normalizedEmail("super@bcwms.com")
            .passwordHash("hidden").displayName("Super").enabled(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformSecurityUser(actor), null,
            List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_SUPER_ADMIN"))));
        OffsetDateTime now = OffsetDateTime.now();
        PlatformAuditLog log = PlatformAuditLog.builder().id(11L).platformUserId(7L).targetTenantId(3L)
            .action("EXPORT").resourceType("users").result("SUCCESS").createdAt(now)
            .requestIp("127.0.0.1").userAgent("secret-browser")
            .detailJson("{\"jobId\":\"job-1\",\"recordCount\":12,\"sha256\":\"hidden\",\"error\":\"hidden\"}")
            .build();
        when(auditRepository.search(any(), any(), isNull(), eq("EXPORT"), any()))
            .thenReturn(new PageImpl<>(List.of(log)));
        when(userRepository.findAllById(any())).thenReturn(List.of(actor));
        when(tenantRepository.findAllById(any())).thenReturn(List.of(
            Tenant.builder().id(3L).displayName("示例租户").tenantCode("T-3").slug("t3").build()));
        PlatformAuditQueryService service = new PlatformAuditQueryService(auditRepository,
            userRepository, tenantRepository, new PlatformAccessGuard(grantRepository), auditService, new ObjectMapper());

        var result = service.search(now.minusDays(1), now, null, "export", 0, 20, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).summary()).containsEntry("jobId", "job-1")
            .containsEntry("recordCount", 12)
            .doesNotContainKeys("sha256", "error", "requestIp", "userAgent");
        assertThat(result.getContent().get(0).actorEmail()).isEqualTo("super@bcwms.com");
        assertThat(result.getContent().get(0).targetTenantName()).isEqualTo("示例租户");
        verify(auditService).record(eq(7L), isNull(), eq("AUDIT_READ"),
            eq("platform_audit_log"), eq("SUCCESS"), anyMap(), isNull());
    }

    @Test void usesTenantTerminologyForLegacyDefaultTenant() {
        PlatformUser actor = PlatformUser.builder().id(7L).normalizedEmail("super@bcwms.com")
            .passwordHash("hidden").displayName("Super").enabled(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformSecurityUser(actor), null,
            List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_SUPER_ADMIN"))));
        OffsetDateTime now = OffsetDateTime.now();
        PlatformAuditLog log = PlatformAuditLog.builder().id(12L).platformUserId(7L).targetTenantId(1L)
            .action("READ").resourceType("users").result("SUCCESS").createdAt(now).build();
        when(auditRepository.search(any(), any(), isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(log)));
        when(userRepository.findAllById(any())).thenReturn(List.of(actor));
        when(tenantRepository.findAllById(any())).thenReturn(List.of(
            Tenant.builder().id(1L).displayName("默认公司").tenantCode("COMPANY-000001")
                .slug("default-company").build()));
        PlatformAuditQueryService service = new PlatformAuditQueryService(auditRepository,
            userRepository, tenantRepository, new PlatformAccessGuard(grantRepository), auditService, new ObjectMapper());

        var result = service.search(now.minusDays(1), now, null, null, 0, 20, null);

        assertThat(result.getContent().get(0).targetTenantName()).isEqualTo("默认租户");
    }
}

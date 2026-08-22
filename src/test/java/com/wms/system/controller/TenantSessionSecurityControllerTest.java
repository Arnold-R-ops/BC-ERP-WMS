package com.wms.system.controller;

import com.wms.system.dto.TenantSessionSecurityAuditDTO;
import com.wms.system.entity.TenantSessionSecurityAudit;
import com.wms.system.repository.TenantSessionSecurityAuditRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantSessionSecurityControllerTest {

    @Mock
    private TenantSessionSecurityAuditRepository auditRepository;

    @InjectMocks
    private TenantSessionSecurityController controller;

    @Test
    void returnsNewestTenantScopedAuditRecordsWithoutSecrets() {
        TenantSessionSecurityAudit audit = TenantSessionSecurityAudit.builder()
            .id(9L)
            .companyId(1L)
            .action("ADMIN_REVOKE_ALL_SESSIONS")
            .operatorId(2L)
            .operatorUsername("admin")
            .targetUserId(69L)
            .targetUsername("employee")
            .reason("Reported suspicious activity")
            .result("SUCCESS")
            .createdAt(LocalDateTime.of(2026, 8, 19, 17, 40, 55))
            .build();
        when(auditRepository.findByCompanyIdOrderByCreatedAtDesc(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(audit));

        ResponseEntity<List<TenantSessionSecurityAuditDTO>> response = controller.listAudits(50);

        assertThat(response.getBody()).containsExactly(new TenantSessionSecurityAuditDTO(
            9L, "ADMIN_REVOKE_ALL_SESSIONS", 2L, "admin", 69L, "employee",
            "Reported suspicious activity", "SUCCESS",
            LocalDateTime.of(2026, 8, 19, 17, 40, 55)
        ));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditRepository).findByCompanyIdOrderByCreatedAtDesc(
            org.mockito.ArgumentMatchers.eq(1L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void clampsAuditLimitToTheSafetyBoundary() {
        when(auditRepository.findByCompanyIdOrderByCreatedAtDesc(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of());

        controller.listAudits(5000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditRepository).findByCompanyIdOrderByCreatedAtDesc(
            org.mockito.ArgumentMatchers.eq(1L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
    }
}

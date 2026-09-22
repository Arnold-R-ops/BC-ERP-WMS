package com.wms.system.platform.service;

import com.wms.system.platform.model.PlatformAuditLog;
import com.wms.system.platform.model.PlatformExportJob;
import com.wms.system.tenant.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformCompanyDataFixtureTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final String id = "11111111-1111-4111-8111-111111111111";
    void ready() {
        when(jdbc.queryForList(startsWith("select id from platform_roles"), eq(Long.class), eq("PLATFORM_TENANT_EXPORT"))).thenReturn(List.of(7L));
        when(jdbc.queryForList(startsWith("select id from platform_users"), eq(Long.class), eq(10L))).thenReturn(List.of(10L));
        when(jdbc.queryForList(startsWith("select platform_role_id"), eq(Long.class), eq(10L))).thenReturn(List.of());
        when(jdbc.queryForObject(startsWith("select count(*)"), eq(Integer.class), eq(10L), eq(20L))).thenReturn(1);
        when(jdbc.update(startsWith("insert into platform_user_roles"), eq(10L), eq(7L))).thenReturn(1);
        when(jdbc.queryForList(startsWith("select r.role_code"), eq(String.class), eq(10L))).thenReturn(List.of("PLATFORM_TENANT_EXPORT"));
    }
    String persist() { return PlatformCompanyDataFixture.persistExportRole(jdbc,10,20); }
    @Test void closedStatesUseExactThirtyDayUtcRetentionInPast() {
        var now=OffsetDateTime.parse("2026-09-18T01:02:03.123456789+08:00");
        for(var status:List.of(TenantStatus.CLOSED,TenantStatus.PURGE_PENDING,TenantStatus.PURGED)) {
            var row=PlatformCompanyDataFixture.retention(Tenant.builder().status(status).build(),now);
            assertThat(row.getClosedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
            assertThat(row.getPurgeDueAt()).isEqualTo(row.getClosedAt().plusDays(30)).isBefore(now);
            assertThat(row.getClosedAt().getNano()).isZero();
            assertThat(row.getStatus()).isEqualTo(status);
        }
    }
    @Test void nonClosedStatesHaveNullRetentionFields() {
        for(var status:TenantStatus.values()) if(!List.of(TenantStatus.CLOSED,TenantStatus.PURGE_PENDING,TenantStatus.PURGED).contains(status)) {
            var row=PlatformCompanyDataFixture.retention(Tenant.builder().status(status).closedAt(OffsetDateTime.now()).purgeDueAt(OffsetDateTime.now()).build(),OffsetDateTime.now());
            assertThat(row.getClosedAt()).isNull(); assertThat(row.getPurgeDueAt()).isNull();
        }
    }
    @Test void exactBaselineRoleGrantAndReadbackDetermineRequestAuthority() {
        ready(); assertThat(persist()).isEqualTo("ROLE_PLATFORM_TENANT_EXPORT");
        verify(jdbc).update("insert into platform_user_roles(platform_user_id,platform_role_id) values(?,?)",10L,7L);
    }
    @Test void absentBaselineRoleFailsWithoutCreatingRole() {
        ready(); when(jdbc.queryForList(startsWith("select id from platform_roles"),eq(Long.class),anyString())).thenReturn(List.of());
        assertThatThrownBy(this::persist).hasMessage("BASELINE_EXPORT_ROLE_MISSING_OR_AMBIGUOUS");
        verify(jdbc,never()).update(anyString(),anyLong(),anyLong());
    }
    @Test void ambiguousBaselineRoleFails() {
        ready(); when(jdbc.queryForList(startsWith("select id from platform_roles"),eq(Long.class),anyString())).thenReturn(List.of(7L,8L));
        assertThatThrownBy(this::persist).hasMessage("BASELINE_EXPORT_ROLE_MISSING_OR_AMBIGUOUS");
    }
    @Test void disabledActorFailsBeforeRoleWrite() {
        ready(); when(jdbc.queryForList(startsWith("select id from platform_users"),eq(Long.class),eq(10L))).thenReturn(List.of());
        assertThatThrownBy(this::persist).hasMessage("EXPORT_ACTOR_NOT_ENABLED");
    }
    @Test void unexpectedExistingRoleIsNotRemovedOrOverwritten() {
        ready(); when(jdbc.queryForList(startsWith("select platform_role_id"),eq(Long.class),eq(10L))).thenReturn(List.of(99L));
        assertThatThrownBy(this::persist).hasMessage("UNEXPECTED_EXPORT_ACTOR_ROLE");
        verify(jdbc,never()).update(anyString(),anyLong(),anyLong());
    }
    @Test void absentExactGrantFails() {
        ready(); when(jdbc.queryForObject(startsWith("select count(*)"),eq(Integer.class),eq(10L),eq(20L))).thenReturn(0);
        assertThatThrownBy(this::persist).hasMessage("EXACT_EXPORT_GRANT_MISSING_OR_AMBIGUOUS");
    }
    @Test void duplicateExactGrantFails() {
        ready(); when(jdbc.queryForObject(startsWith("select count(*)"),eq(Integer.class),eq(10L),eq(20L))).thenReturn(2);
        assertThatThrownBy(this::persist).hasMessage("EXACT_EXPORT_GRANT_MISSING_OR_AMBIGUOUS");
    }
    @Test void roleInsertCountMustBeOne() {
        ready(); when(jdbc.update(startsWith("insert into platform_user_roles"),eq(10L),eq(7L))).thenReturn(0);
        assertThatThrownBy(this::persist).hasMessage("EXPORT_ROLE_INSERT_COUNT");
    }
    @Test void roleReadbackCannotSilentlyBecomeSuperAdmin() {
        ready(); when(jdbc.queryForList(startsWith("select r.role_code"),eq(String.class),eq(10L))).thenReturn(List.of("PLATFORM_SUPER_ADMIN"));
        assertThatThrownBy(this::persist).hasMessage("EXPORT_ROLE_READBACK_MISMATCH");
    }
    @Test void nonPositiveIdentityFailsWithoutQuery() {
        assertThatThrownBy(()->PlatformCompanyDataFixture.persistExportRole(jdbc,0,20)).hasMessage("INVALID_FIXTURE_ID"); verifyNoInteractions(jdbc);
    }
    PlatformExportJob job() { return PlatformExportJob.builder().publicId(id).platformUserId(10L).targetTenantId(20L).requestedResource("warehouses").status("FAILED").errorCode("PLATFORM_EXPORT_FAILED").build(); }
    PlatformAuditLog audit(String job,String type) { return PlatformAuditLog.builder().platformUserId(10L).targetTenantId(20L).action("EXPORT").resourceType("warehouses").result("FAILED").detailJson("{\"jobId\":\""+job+"\",\"error\":\""+type+"\",\"password\":\"synthetic-do-not-log\"}").build(); }
    String diagnose(List<PlatformAuditLog> logs) { return PlatformCompanyDataFixture.diagnostic(job(),id,10,20,logs); }
    @Test void diagnosticDistinguishesGenericCodeFromAuditExceptionType() {
        assertThat(diagnose(List.of(audit(id,"TenantAccessException"))))
            .contains("publicErrorCode=PLATFORM_EXPORT_FAILED","matchedFailureAudits=1","auditExceptionTypes=[TenantAccessException]","underlyingErrorCode=NOT_CAPTURED")
            .doesNotContain("synthetic-do-not-log","password");
    }
    @Test void unrelatedJobActorTenantDatasetAndActionAreExcluded() {
        var actor=audit(id,"WrongActorException");actor.setPlatformUserId(11L);
        var tenant=audit(id,"WrongTenantException");tenant.setTargetTenantId(21L);
        var dataset=audit(id,"WrongDatasetException");dataset.setResourceType("users");
        var action=audit(id,"WrongActionException");action.setAction("READ");
        assertThat(diagnose(List.of(actor,tenant,dataset,action,audit(UUID.randomUUID().toString(),"WrongJobException"))))
            .contains("matchedFailureAudits=0","auditExceptionTypes=[]").doesNotContain("Wrong");
    }
    @Test void arbitraryAuditTextIsRedacted() {
        assertThat(diagnose(List.of(audit(id,"secret token value")))).contains("auditExceptionTypes=[UNAVAILABLE]").doesNotContain("secret token value");
    }
    @Test void malformedAuditIsCountedNotPrinted() {
        var row=audit(id,"Error");row.setDetailJson("sensitive broken json");
        assertThat(diagnose(List.of(row))).contains("malformedCandidateAudits=1").doesNotContain("sensitive");
    }
    @Test void foreignJobIdentityIsRejected() {
        var row=job();row.setTargetTenantId(21L);
        assertThatThrownBy(()->PlatformCompanyDataFixture.requireJob(row,id,10,20)).hasMessage("EXPORT_JOB_OWNERSHIP_MISMATCH");
    }
    @Test void unknownPublicCodeAndStatusAreNotLogged() {
        var row=job();row.setErrorCode("synthetic-secret");row.setStatus("unsafe-state");
        assertThat(PlatformCompanyDataFixture.diagnostic(row,id,10,20,List.of())).contains("UNRECOGNIZED_REDACTED").doesNotContain("synthetic-secret","unsafe-state");
    }
}

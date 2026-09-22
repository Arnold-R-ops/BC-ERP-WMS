package com.wms.system.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.platform.model.PlatformAuditLog;
import com.wms.system.platform.model.PlatformExportJob;
import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.model.TenantStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Test-only fixtures. No production rules, schema or background checks are replaced. */
final class PlatformCompanyDataFixture {
    static final String EXPORT_ROLE = "PLATFORM_TENANT_EXPORT";
    private PlatformCompanyDataFixture() { }

    static Tenant retention(Tenant tenant, OffsetDateTime now) {
        if (List.of(TenantStatus.CLOSED, TenantStatus.PURGE_PENDING, TenantStatus.PURGED).contains(tenant.getStatus())) {
            var closed = now.withOffsetSameInstant(ZoneOffset.UTC).minusDays(31).withNano(0);
            tenant.setClosedAt(closed);
            tenant.setPurgeDueAt(closed.plusDays(30));
        } else {
            tenant.setClosedAt(null);
            tenant.setPurgeDueAt(null);
        }
        return tenant;
    }

    // Called inside the test's transaction, after its exact owned actor and grant are registered.
    static String persistExportRole(JdbcTemplate jdbc, long actor, long tenant) {
        if (actor <= 0 || tenant <= 0) throw new IllegalStateException("INVALID_FIXTURE_ID");
        var roles = jdbc.queryForList("select id from platform_roles where role_code=?", Long.class, EXPORT_ROLE);
        if (roles.size() != 1 || roles.get(0) <= 0) throw new IllegalStateException("BASELINE_EXPORT_ROLE_MISSING_OR_AMBIGUOUS");
        var actors = jdbc.queryForList("select id from platform_users where id=? and enabled=true for update", Long.class, actor);
        if (!actors.equals(List.of(actor))) throw new IllegalStateException("EXPORT_ACTOR_NOT_ENABLED");
        var existing = jdbc.queryForList("select platform_role_id from platform_user_roles where platform_user_id=?", Long.class, actor);
        if (!existing.isEmpty()) throw new IllegalStateException("UNEXPECTED_EXPORT_ACTOR_ROLE");
        Integer grants = jdbc.queryForObject("select count(*) from platform_access_grants where grantee_platform_user_id=? and tenant_id=? and capability='EXPORT' and dataset_code='warehouses' and revoked_at is null and effective_from<=current_timestamp and expires_at>current_timestamp", Integer.class, actor, tenant);
        if (!Objects.equals(grants, 1)) throw new IllegalStateException("EXACT_EXPORT_GRANT_MISSING_OR_AMBIGUOUS");
        if (jdbc.update("insert into platform_user_roles(platform_user_id,platform_role_id) values(?,?)", actor, roles.get(0)) != 1)
            throw new IllegalStateException("EXPORT_ROLE_INSERT_COUNT");
        var actual = jdbc.queryForList("select r.role_code from platform_user_roles ur join platform_roles r on r.id=ur.platform_role_id where ur.platform_user_id=?", String.class, actor);
        if (!actual.equals(List.of(EXPORT_ROLE))) throw new IllegalStateException("EXPORT_ROLE_READBACK_MISMATCH");
        return "ROLE_" + actual.get(0);
    }

    static void requireJob(PlatformExportJob job, String publicId, long actor, long tenant) {
        if (job == null || !Objects.equals(job.getPublicId(), publicId) || !Objects.equals(job.getPlatformUserId(), actor)
                || !Objects.equals(job.getTargetTenantId(), tenant) || !"warehouses".equals(job.getRequestedResource()))
            throw new IllegalStateException("EXPORT_JOB_OWNERSHIP_MISMATCH");
    }

    // Never serialize an entity, Throwable, request, raw detail_json or exception message.
    static String diagnostic(PlatformExportJob job, String publicId, long actor, long tenant, List<PlatformAuditLog> audits) {
        if (!UUID.fromString(publicId).toString().equals(publicId)) throw new IllegalStateException("INVALID_PUBLIC_JOB_ID");
        requireJob(job, publicId, actor, tenant);
        var types = new java.util.ArrayList<String>();
        int matched = 0, malformed = 0;
        for (var audit : audits) {
            if (!Objects.equals(audit.getPlatformUserId(), actor) || !Objects.equals(audit.getTargetTenantId(), tenant)
                    || !"EXPORT".equals(audit.getAction()) || !"warehouses".equals(audit.getResourceType()) || !"FAILED".equals(audit.getResult())) continue;
            try {
                var detail = new ObjectMapper().readTree(audit.getDetailJson());
                if (detail == null || !publicId.equals(detail.path("jobId").asText())) continue;
                matched++;
                String type = detail.path("error").asText();
                types.add(type.matches("[A-Za-z][A-Za-z0-9_$]{0,119}(?:Exception|Error)") ? type : "UNAVAILABLE");
            } catch (Exception ignored) { malformed++; }
        }
        String code = "PLATFORM_EXPORT_FAILED".equals(job.getErrorCode()) ? "PLATFORM_EXPORT_FAILED" : job.getErrorCode() == null ? "NONE" : "UNRECOGNIZED_REDACTED";
        String state = List.of("PENDING", "RUNNING", "FAILED", "COMPLETED", "EXPIRED").contains(job.getStatus()) ? job.getStatus() : "UNRECOGNIZED_REDACTED";
        return "PLATFORM_EXPORT_FIXTURE_DIAGNOSTIC job=" + publicId + " actor=" + actor + " tenant=" + tenant
            + " state=" + state + " publicErrorCode=" + code + " matchedFailureAudits=" + matched
            + " auditExceptionTypes=" + types + " malformedCandidateAudits=" + malformed + " underlyingErrorCode=NOT_CAPTURED";
    }
}

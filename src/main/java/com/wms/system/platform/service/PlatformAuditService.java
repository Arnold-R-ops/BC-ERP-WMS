package com.wms.system.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.platform.model.PlatformAuditLog;
import com.wms.system.platform.repository.PlatformAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformAuditService {
    private final PlatformAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, Long companyId, String action, String resource,
                       String result, Map<String, ?> detail, HttpServletRequest request) {
        persist(actorId, companyId, null, action, resource, result, detail, request, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAdmin(Long actorId, Long targetPlatformUserId, String action, String resource,
                            String result, Map<String, ?> detail, HttpServletRequest request) {
        persist(actorId, null, targetPlatformUserId, action, resource, result, detail, request, false);
    }

    /**
     * Writes an audit row atomically with a transaction that is creating the
     * platform actor itself.  A REQUIRES_NEW audit transaction cannot see that
     * uncommitted actor and would violate the audit-log foreign key.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInCurrentTransaction(Long actorId, Long companyId, String action, String resource,
                                           String result, Map<String, ?> detail, HttpServletRequest request) {
        persist(actorId, companyId, null, action, resource, result, detail, request, true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAdminInCurrentTransaction(
        Long actorId,
        Long targetPlatformUserId,
        String action,
        String resource,
        String result,
        Map<String, ?> detail,
        HttpServletRequest request
    ) {
        persist(actorId, null, targetPlatformUserId, action, resource, result, detail, request, true);
    }

    private void persist(Long actorId, Long companyId, Long targetPlatformUserId, String action, String resource,
                         String result, Map<String, ?> detail, HttpServletRequest request,
                         boolean flush) {
        PlatformAuditLog log = PlatformAuditLog.builder()
            .platformUserId(actorId).targetTenantId(companyId).targetPlatformUserId(targetPlatformUserId).action(action)
            .resourceType(resource).requestId(request == null ? null : request.getHeader("X-Request-ID"))
            .requestIp(request == null ? null : request.getRemoteAddr())
            .userAgent(request == null ? null : truncate(request.getHeader("User-Agent"), 500))
            .result(result).detailJson(json(detail)).build();
        if (flush) repository.saveAndFlush(log); else repository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBackground(Long actorId, Long companyId, String action, String resource,
                                 String result, Map<String, ?> detail) {
        record(actorId, companyId, action, resource, result, detail, null);
    }

    private String json(Map<String, ?> detail) {
        try { return objectMapper.writeValueAsString(detail == null ? Map.of() : detail); }
        catch (JsonProcessingException ignored) { return "{}"; }
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}

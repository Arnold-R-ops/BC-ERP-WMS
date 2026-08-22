package com.wms.system.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.entity.Customer;
import com.wms.system.entity.Product;
import com.wms.system.platform.dto.PlatformOperationRequest;
import com.wms.system.platform.dto.PlatformOperationResponse;
import com.wms.system.platform.model.PlatformOperationAuthorization;
import com.wms.system.platform.repository.PlatformOperationAuthorizationRepository;
import com.wms.system.security.PlatformSecurityUser;
import com.wms.system.security.SecurityUser;
import com.wms.system.tenant.context.*;
import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.model.TenantStatus;
import com.wms.system.tenant.repository.TenantRepository;
import com.wms.system.repository.CustomerRepository;
import com.wms.system.repository.ProductRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlatformOperationAuthorizationService {
    private final PlatformOperationAuthorizationRepository repository;
    private final TenantRepository tenantRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final PlatformAccessGuard guard;
    private final PlatformAuditService audit;
    private final ObjectMapper mapper;
    private final PlatformTransactionManager transactionManager;

    public PlatformOperationResponse request(Long tenantId, PlatformOperationRequest request, HttpServletRequest http) {
        String operation = normalizedOperation(request.operationType());
        PlatformSecurityUser actor = "WRITE".equals(operation) ? guard.requireWrite() : guard.requireDelete();
        PlatformOperationCatalog.validate(operation, request.resourceType(), request.payload());
        Tenant tenant = requireOperable(tenantId);
        String payload = json(canonical(request.payload()));
        PlatformOperationAuthorization authorization = PlatformOperationAuthorization.builder()
            .publicId(UUID.randomUUID().toString()).tenantId(tenantId).operationType(operation)
            .status("PENDING").requestedByPlatformUserId(actor.getId())
            .resourceType(request.resourceType()).resourceId(request.resourceId()).resourceScope(scope(request))
            .requestPayloadJson(payload).requestFingerprint(fingerprint(operation, request.resourceType(), request.resourceId(), payload))
            // Pending requests expire after seven days; approval resets this to the
            // actual operation capability window below.
            .expiresAt(OffsetDateTime.now().plusDays(7))
            .reason(trimReason(request.reason())).build();
        repository.save(authorization);
        audit.record(actor.getId(), tenant.getId(), "OPERATION_REQUESTED", request.resourceType(), "SUCCESS",
            Map.of("authorizationId", authorization.getPublicId(), "operation", operation,
                "resourceId", request.resourceId(), "fingerprint", authorization.getRequestFingerprint()), http);
        return response(authorization);
    }

    public List<PlatformOperationResponse> companyRequests() {
        SecurityUser admin = requireTenantAdmin();
        return repository.findByTenantIdOrderByCreatedAtDesc(admin.getUser().getCompanyId()).stream()
            .map(this::response).toList();
    }

    public PlatformOperationResponse platformRequest(String publicId) {
        PlatformSecurityUser actor = platformActorForLookup();
        PlatformOperationAuthorization authorization = repository.findByPublicId(publicId)
            .orElseThrow(() -> new NoSuchElementException("Operation request not found"));
        if (!Objects.equals(authorization.getRequestedByPlatformUserId(), actor.getId())
                && !hasPlatformSuperAdmin()) {
            throw new AccessDeniedException("Operation request is not visible to this platform user");
        }
        return response(authorization);
    }

    public PlatformOperationResponse decide(String publicId, String decision, HttpServletRequest http) {
        SecurityUser admin = requireTenantAdmin();
        return inTransaction(() -> {
            PlatformOperationAuthorization authorization = repository.findByPublicIdAndTenantId(publicId,
                admin.getUser().getCompanyId()).orElseThrow(() -> new NoSuchElementException("Operation request not found"));
            if (!"PENDING".equals(authorization.getStatus())) throw new IllegalStateException("Operation request is no longer pending");
            OffsetDateTime now = OffsetDateTime.now();
            if (!now.isBefore(authorization.getExpiresAt())) {
                authorization.setStatus("EXPIRED"); repository.save(authorization); return response(authorization);
            }
            if ("APPROVE".equalsIgnoreCase(decision)) {
                authorization.setStatus("APPROVED"); authorization.setApprovedByTenantUserId(admin.getId()); authorization.setApprovedAt(now);
                authorization.setExpiresAt(now.plusHours("DELETE".equals(authorization.getOperationType()) ? 1 : 24));
            } else if ("REVOKE".equalsIgnoreCase(decision)) {
                authorization.setStatus("REVOKED"); authorization.setRevokedAt(now);
            } else throw new IllegalArgumentException("Unsupported operation decision");
            repository.save(authorization);
            return response(authorization);
        });
    }

    public PlatformOperationResponse execute(String publicId, String executionKey, HttpServletRequest http) {
        if (executionKey == null || executionKey.isBlank() || executionKey.length() > 80) throw new IllegalArgumentException("Execution key is required");
        PlatformSecurityUser actor = platformActorForLookup();
        PlatformOperationAuthorization authorization = repository.findByPublicId(publicId)
            .orElseThrow(() -> new NoSuchElementException("Operation request not found"));
        if (!Objects.equals(authorization.getRequestedByPlatformUserId(), actor.getId())
                && !hasPlatformSuperAdmin()) throw new AccessDeniedException("Operation request is not executable by this platform user");
        PlatformSecurityUser executionActor = "WRITE".equals(authorization.getOperationType())
            ? guard.requireWrite() : guard.requireDelete();
        Tenant tenant = requireOperable(authorization.getTenantId());
        return TenantContextHolder.runWithResolvedTenant(context(tenant),
            () -> inTransaction(() -> executeLocked(authorization, executionActor, executionKey, http)));
    }

    private PlatformOperationResponse executeLocked(PlatformOperationAuthorization reference, PlatformSecurityUser actor,
                                                    String executionKey, HttpServletRequest http) {
        PlatformOperationAuthorization authorization = repository.findByIdForUpdate(reference.getId()).orElseThrow();
        if (authorization.getConsumedAt() != null) {
            if (executionKey.equals(authorization.getExecutionKey())) return response(authorization);
            throw new IllegalStateException("Operation authorization has already been consumed");
        }
        if (!"APPROVED".equals(authorization.getStatus()) || authorization.getRevokedAt() != null
                || !OffsetDateTime.now().isBefore(authorization.getExpiresAt())) throw new IllegalStateException("Operation authorization is not active");
        Map<String, Object> payload = map(authorization.getRequestPayloadJson());
        String before = snapshot(authorization);
        try {
            apply(authorization, payload);
            String after = snapshot(authorization);
            authorization.setStatus("CONSUMED"); authorization.setConsumedAt(OffsetDateTime.now());
            authorization.setExecutedByPlatformUserId(actor.getId()); authorization.setExecutionKey(executionKey); repository.save(authorization);
            audit.record(actor.getId(), authorization.getTenantId(), "OPERATION_EXECUTED", authorization.getResourceType(), "SUCCESS",
                Map.of("authorizationId", authorization.getPublicId(), "operation", authorization.getOperationType(),
                    "resourceId", authorization.getResourceId(), "before", before, "after", after), http);
            return response(authorization);
        } catch (RuntimeException exception) {
            audit.record(actor.getId(), authorization.getTenantId(), "OPERATION_EXECUTED", authorization.getResourceType(), "FAILED",
                Map.of("authorizationId", authorization.getPublicId(), "error", exception.getClass().getSimpleName()), http);
            throw exception;
        }
    }

    private void apply(PlatformOperationAuthorization auth, Map<String, Object> payload) {
        Long id = Long.valueOf(auth.getResourceId());
        if ("product".equals(auth.getResourceType())) {
            Product product = productRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Product not found"));
            if ("DELETE".equals(auth.getOperationType())) product.setEnabled(false);
            else { if (payload.containsKey("productName")) product.setProductName(string(payload, "productName")); if (payload.containsKey("brand")) product.setBrand(string(payload, "brand")); if (payload.containsKey("description")) product.setDescription(string(payload, "description")); }
            productRepository.save(product);
        } else if ("customer".equals(auth.getResourceType())) {
            Customer customer = customerRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Customer not found"));
            if ("DELETE".equals(auth.getOperationType())) customer.setIsActive(false);
            else { if (payload.containsKey("name")) customer.setName(string(payload, "name")); if (payload.containsKey("contact")) customer.setContact(string(payload, "contact")); if (payload.containsKey("phone")) customer.setPhone(string(payload, "phone")); if (payload.containsKey("email")) customer.setEmail(string(payload, "email")); if (payload.containsKey("address")) customer.setAddress(string(payload, "address")); if (payload.containsKey("creditLimit")) customer.setCreditLimit(new BigDecimal(String.valueOf(payload.get("creditLimit")))); if (payload.containsKey("isActive")) customer.setIsActive(Boolean.valueOf(String.valueOf(payload.get("isActive")))); }
            customerRepository.save(customer);
        } else throw new IllegalArgumentException("Unsupported operation resource");
    }

    private String snapshot(PlatformOperationAuthorization auth) {
        Long id = Long.valueOf(auth.getResourceId());
        if ("product".equals(auth.getResourceType())) { Product p = productRepository.findById(id).orElseThrow(); return json(Map.of("id", p.getId(), "name", p.getProductName(), "brand", Optional.ofNullable(p.getBrand()).orElse(""), "description", Optional.ofNullable(p.getDescription()).orElse(""), "enabled", p.getEnabled())); }
        Customer c = customerRepository.findById(id).orElseThrow();
        return json(Map.of("id", c.getId(), "name", c.getName(), "contact", Optional.ofNullable(c.getContact()).orElse(""), "phone", Optional.ofNullable(c.getPhone()).orElse(""), "email", Optional.ofNullable(c.getEmail()).orElse(""), "address", Optional.ofNullable(c.getAddress()).orElse(""), "creditLimit", c.getCreditLimit(), "isActive", c.getIsActive()));
    }
    private SecurityUser requireTenantAdmin() { Authentication a = SecurityContextHolder.getContext().getAuthentication(); if (!(a != null && a.getPrincipal() instanceof SecurityUser u) || a.getAuthorities().stream().noneMatch(x -> "TENANT_ADMIN".equals(x.getAuthority()))) throw new AccessDeniedException("Company administrator approval is required"); return u; }
    private PlatformSecurityUser platformActorForLookup() { Authentication a = SecurityContextHolder.getContext().getAuthentication(); if (a == null || !(a.getPrincipal() instanceof PlatformSecurityUser u)) throw new AccessDeniedException("Platform authentication is required"); return u; }
    private boolean hasPlatformSuperAdmin() { Authentication a = SecurityContextHolder.getContext().getAuthentication(); return a != null && a.getAuthorities().stream().anyMatch(x -> "ROLE_PLATFORM_SUPER_ADMIN".equals(x.getAuthority())); }
    private Tenant requireOperable(Long id) { return tenantRepository.findById(id).filter(t -> t.getStatus() == TenantStatus.ACTIVE || t.getStatus() == TenantStatus.SUSPENDED).orElseThrow(() -> new IllegalStateException("Company is not operable")); }
    private TenantContext context(Tenant t) { return new TenantContext(t.getId(), t.getSlug(), t.getSlug() + ".bcwms.com", RequestSurface.TENANT, t.getStatus()); }
    private String normalizedOperation(String operation) { String result = operation == null ? "" : operation.trim().toUpperCase(Locale.ROOT); if (!Set.of("WRITE","DELETE").contains(result)) throw new IllegalArgumentException("Unsupported operation type"); return result; }
    private String scope(PlatformOperationRequest r) { return r.resourceType() + ":" + r.resourceId(); }
    private String trimReason(String value) { String result = value == null ? "" : value.trim(); if (result.isEmpty() || result.length() > 500) throw new IllegalArgumentException("Operation reason is required"); return result; }
    private String string(Map<String,Object> m, String key) { Object v=m.get(key); if (v == null || String.valueOf(v).isBlank()) throw new IllegalArgumentException("Invalid operation field"); return String.valueOf(v).trim(); }
    private Map<String,Object> canonical(Map<String,Object> source) { return new TreeMap<>(source); }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("Operation payload cannot be encoded", e); } }
    private Map<String,Object> map(String value) { try { return mapper.readValue(value, new TypeReference<>() {}); } catch (Exception e) { throw new IllegalStateException("Operation payload is invalid", e); } }
    private String fingerprint(String op, String type, String id, String payload) { try { byte[] bytes=MessageDigest.getInstance("SHA-256").digest((op+"|"+type+"|"+id+"|"+payload).getBytes(StandardCharsets.UTF_8)); return java.util.HexFormat.of().formatHex(bytes); } catch (Exception e) { throw new IllegalStateException(e); } }
    private PlatformOperationResponse response(PlatformOperationAuthorization a) { return new PlatformOperationResponse(a.getPublicId(), a.getTenantId(), a.getOperationType(), a.getResourceType(), a.getResourceId(), map(a.getRequestPayloadJson()), a.getStatus(), a.getReason(), a.getExpiresAt(), a.getApprovedAt(), a.getRevokedAt(), a.getConsumedAt()); }
    private <T> T inTransaction(java.util.function.Supplier<T> action) { return new TransactionTemplate(transactionManager).execute(status -> action.get()); }
}

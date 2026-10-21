package com.wms.system.service.inventory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.inventory.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.IdempotencyService;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryPackagingCommandService {
    private final InventoryPackagingService executor;
    private final IdempotencyService idempotency;
    private final ObjectMapper mapper;

    public List<PackagingView> arrange(ArrangePackagingRequest request, Authentication auth, String key) {
        if (key == null || key.isBlank() || key.length() > 120) throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED");
        String intent;
        try { intent = mapper.writeValueAsString(Map.of("request", request, "operator", AuthUserResolver.resolveUserId(auth))); }
        catch (Exception e) { throw new IllegalStateException(e); }
        var claim = idempotency.claim(key, "INVENTORY_PACKAGING_ARRANGE", intent);
        if (claim.decision() == IdempotencyClaimDecision.IN_PROGRESS) throw new BusinessException("IDEMPOTENCY_REQUEST_IN_PROGRESS");
        if (claim.decision() == IdempotencyClaimDecision.REPLAY_FAILED) throw new BusinessException(claim.request().getResponseBody());
        if (claim.decision() == IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            // Recheck current warehouse permissions even when returning a committed result.
            request.sources().forEach(s -> executor.get(s.batchId(), auth));
            try { return mapper.readValue(claim.request().getResponseBody(), new TypeReference<List<PackagingView>>() {}); }
            catch (Exception e) { throw new BusinessException("IDEMPOTENCY_CONFLICT"); }
        }
        try { return executor.arrange(request, auth, claim.request(), key); }
        catch (RuntimeException e) {
            idempotency.markFailed(claim.request(), 409, e instanceof BusinessException b ? b.getErrorKey() : "INTERNAL_SERVER_ERROR");
            throw e;
        }
    }
}

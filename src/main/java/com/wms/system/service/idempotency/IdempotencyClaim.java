package com.wms.system.service.idempotency;

import com.wms.system.entity.IdempotencyRequest;

import java.util.Objects;

/**
 * A typed claim result for callers that need to distinguish an execution
 * lease from a replayed terminal response.
 */
public record IdempotencyClaim(
    IdempotencyClaimDecision decision,
    IdempotencyRequest request
) {
    public IdempotencyClaim {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(request, "request");
    }

    public boolean shouldExecute() {
        return decision == IdempotencyClaimDecision.ACQUIRED;
    }

    public boolean isReplay() {
        return decision == IdempotencyClaimDecision.REPLAY_SUCCEEDED
            || decision == IdempotencyClaimDecision.REPLAY_FAILED;
    }
}

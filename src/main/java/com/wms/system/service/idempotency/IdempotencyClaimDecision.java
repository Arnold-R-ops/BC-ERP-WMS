package com.wms.system.service.idempotency;

/** Result of claiming a company-scoped idempotency key. */
public enum IdempotencyClaimDecision {
    ACQUIRED,
    IN_PROGRESS,
    REPLAY_SUCCEEDED,
    REPLAY_FAILED
}

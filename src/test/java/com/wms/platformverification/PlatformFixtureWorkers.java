package com.wms.platformverification;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Test-only close barrier. A failure leaves all associated fixtures intact. */
public final class PlatformFixtureWorkers {
    private PlatformFixtureWorkers() { }
    public static void requireStopped(ExecutorService executor, Duration budget) {
        if (budget.isZero() || budget.isNegative() || budget.compareTo(Duration.ofSeconds(10)) > 0)
            throw new IllegalArgumentException("INVALID_WORKER_CLOSE_BUDGET");
        if (executor == null) return;
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(budget.toNanos(), TimeUnit.NANOSECONDS) || !executor.isTerminated())
                throw new IllegalStateException("WORKERS_NOT_TERMINATED_FIXTURES_RETAINED");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WORKER_CLOSE_INTERRUPTED_FIXTURES_RETAINED", failure);
        }
    }
}

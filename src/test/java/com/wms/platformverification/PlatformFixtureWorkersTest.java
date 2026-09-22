package com.wms.platformverification;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformFixtureWorkersTest {
    @Test void nullExecutorHasNoWork() { PlatformFixtureWorkers.requireStopped(null,Duration.ofSeconds(5)); }
    @Test void rejectsUnboundedBudget() {
        assertThrows(IllegalArgumentException.class,()->PlatformFixtureWorkers.requireStopped(null,Duration.ofSeconds(11)));
        assertThrows(IllegalArgumentException.class,()->PlatformFixtureWorkers.requireStopped(null,Duration.ZERO));
    }
    @Test void confirmedTerminationPermitsCleanup() throws Exception {
        var executor=mock(ExecutorService.class);
        when(executor.awaitTermination(anyLong(),eq(TimeUnit.NANOSECONDS))).thenReturn(true);
        when(executor.isTerminated()).thenReturn(true);
        PlatformFixtureWorkers.requireStopped(executor,Duration.ofSeconds(5));
        var order=inOrder(executor); order.verify(executor).shutdownNow(); order.verify(executor).awaitTermination(anyLong(),eq(TimeUnit.NANOSECONDS)); order.verify(executor).isTerminated();
    }
    @Test void timeoutRefusesCleanup() throws Exception {
        var executor=mock(ExecutorService.class); var cleaned=new java.util.concurrent.atomic.AtomicBoolean();
        assertThrows(IllegalStateException.class,()->{PlatformFixtureWorkers.requireStopped(executor,Duration.ofMillis(5));cleaned.set(true);});
        assertFalse(cleaned.get());
    }
    @Test void inconsistentTerminationRefusesCleanup() throws Exception {
        var executor=mock(ExecutorService.class);
        when(executor.awaitTermination(anyLong(),eq(TimeUnit.NANOSECONDS))).thenReturn(true);
        assertThrows(IllegalStateException.class,()->PlatformFixtureWorkers.requireStopped(executor,Duration.ofMillis(5)));
    }
    @Test void interruptionIsPreservedAndRefusesCleanup() throws Exception {
        var executor=mock(ExecutorService.class);
        when(executor.awaitTermination(anyLong(),eq(TimeUnit.NANOSECONDS))).thenThrow(new InterruptedException());
        try { assertThrows(IllegalStateException.class,()->PlatformFixtureWorkers.requireStopped(executor,Duration.ofSeconds(5))); assertTrue(Thread.currentThread().isInterrupted()); }
        finally { Thread.interrupted(); }
    }
    @Test void actualParallelWorkersEndBeforeReturn() throws Exception {
        var executor=Executors.newFixedThreadPool(2); var started=new CountDownLatch(2); var ended=new CountDownLatch(2);
        try {
            for(int i=0;i<2;i++) executor.submit(()->{started.countDown();try {new CountDownLatch(1).await();}catch(InterruptedException expected){Thread.currentThread().interrupt();}finally{ended.countDown();}});
            assertTrue(started.await(5,TimeUnit.SECONDS));
            PlatformFixtureWorkers.requireStopped(executor,Duration.ofSeconds(5));
            assertEquals(0,ended.getCount()); assertTrue(executor.isTerminated());
        } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS)); }
    }
    @Test void actualStuckWorkerRetainedUntilExplicitRelease() throws Exception {
        var executor=Executors.newSingleThreadExecutor(); var started=new CountDownLatch(1); var release=new CountDownLatch(1);
        try {
            executor.submit(()->{started.countDown();while(release.getCount()>0){try{release.await();}catch(InterruptedException ignored){}}});
            assertTrue(started.await(5,TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class,()->PlatformFixtureWorkers.requireStopped(executor,Duration.ofMillis(20)));
            assertFalse(executor.isTerminated());
        } finally { release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS)); }
    }
}

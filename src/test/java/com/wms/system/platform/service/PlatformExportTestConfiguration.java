package com.wms.system.platform.service;

import com.wms.system.platform.config.PlatformAccessProperties;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/** Wraps the real named executor only in the importing integration test context. */
@TestConfiguration
public class PlatformExportTestConfiguration {
    @Bean static BeanPostProcessor trackPlatformExportTasks() {
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String name) {
                return "platformExportExecutor".equals(name) ? new TrackingExecutor((Executor) bean) : bean;
            }
        };
    }

    public static final class TrackingExecutor implements Executor, DisposableBean {
        final Executor delegate;
        private Lease active;
        public TrackingExecutor(Executor delegate) { this.delegate = delegate; }
        public synchronized void requireIdle() {
            if (active != null && !active.finished()) throw new IllegalStateException("EXPORT_WORKER_STILL_ACTIVE");
        }
        public synchronized Lease begin(PlatformAccessProperties properties, String owner, Duration budget) throws IOException {
            requireIdle();
            if (active != null && !active.cleaned) throw new IllegalStateException("PREVIOUS_EXPORT_CLEANUP_NOT_VERIFIED");
            active = new Lease(properties, owner, budget);
            return active;
        }
        @Override public synchronized void execute(Runnable command) {
            if (active == null) throw new IllegalStateException("EXPORT_WITHOUT_OWNED_DIRECTORY");
            Lease lease = active;
            lease.submitted();
            try {
                delegate.execute(() -> {
                    try { command.run(); }
                    finally { lease.completed(); }
                });
            } catch (RuntimeException | Error failure) { lease.completed(); throw failure; }
        }
        @Override public void destroy() throws Exception {
            if (delegate instanceof DisposableBean disposable) disposable.destroy();
        }
    }

    public static final class Lease implements AutoCloseable {
        final Path root;
        final PlatformAccessProperties properties;
        final Path previous;
        final Object directoryKey;
        final java.nio.file.attribute.FileTime createdAt;
        final Path marker;
        final String markerContents;
        final String owner;
        final long budgetNanos;
        private String jobId;
        private int submissions;
        private boolean done, closing, deferred;
        private volatile boolean cleaned;
        private Exception cleanupFailure;

        Lease(PlatformAccessProperties properties, String owner, Duration budget) throws IOException {
            if (budget.isNegative() || budget.isZero() || budget.compareTo(Duration.ofSeconds(30)) > 0)
                throw new IllegalArgumentException("INVALID_EXPORT_WAIT_BUDGET");
            if (!owner.matches("[A-Za-z0-9_.$:-]{1,240}")) throw new IllegalArgumentException("INVALID_FIXTURE_OWNER");
            this.owner = owner; this.properties = properties; this.previous = properties.getExportDirectory();
            budgetNanos = budget.toNanos();
            String configured = System.getProperty("p3.export.fixture-temp-root");
            if (configured == null) throw new IOException("EXPLICIT_EXPORT_FIXTURE_TEMP_ROOT_REQUIRED");
            Path parent = Path.of(configured).toAbsolutePath().normalize();
            if (!Path.of(configured).isAbsolute() || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                    || !parent.toRealPath().equals(parent)) throw new IOException("INVALID_EXPORT_FIXTURE_TEMP_ROOT");
            root = Files.createTempDirectory(parent, "owned-export-").toAbsolutePath().normalize();
            // Record the exact newly created path before any subsequent identity query can fail.
            System.out.println("PLATFORM_EXPORT_DIRECTORY owner=" + owner + " root=" + root + " state=CREATED");
            var attrs = Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            directoryKey = attrs.fileKey(); // Windows may return null; marker + creation time are always required.
            createdAt = attrs.creationTime();
            marker = root.resolve(".fixture-owner");
            markerContents = owner + ":" + UUID.randomUUID();
            Files.writeString(marker, markerContents, StandardOpenOption.CREATE_NEW);
            properties.setExportDirectory(root);
            System.out.println("PLATFORM_EXPORT_DIRECTORY owner=" + owner + " root=" + root + " state=REGISTERED");
        }
        public synchronized void bind(String id) {
            if (jobId != null || closing || !UUID.fromString(id).toString().equals(id)) throw new IllegalStateException("INVALID_EXPORT_JOB_BINDING");
            jobId = id;
            System.out.println("PLATFORM_EXPORT_FILE owner=" + owner + " job=" + id + " file=" + root.resolve(id + ".zip") + " state=BOUND");
        }
        synchronized void submitted() {
            if (closing || submissions != 0) throw new IllegalStateException("UNEXPECTED_EXPORT_SUBMISSION");
            submissions++;
        }
        synchronized boolean finished() { return submissions == 0 || done; }
        synchronized void completed() {
            done = true;
            if (deferred) {
                try { cleanup(); }
                catch (Exception error) { cleanupFailure = error; System.out.println("PLATFORM_EXPORT_DIRECTORY owner=" + owner + " state=DEFERRED_CLEANUP_FAILED type=" + error.getClass().getSimpleName()); }
            }
            notifyAll();
        }
        public synchronized void awaitWorker() throws InterruptedException {
            long end = System.nanoTime() + budgetNanos;
            while (!finished()) {
                long left = end - System.nanoTime();
                if (left <= 0) throw new IllegalStateException("EXPORT_WORKER_WAIT_TIMED_OUT");
                TimeUnit.NANOSECONDS.timedWait(this, left);
            }
            if (submissions != 1) throw new IllegalStateException("EXPORT_SUBMISSION_COUNT_MISMATCH");
        }
        public synchronized Path artifact(String id) {
            if (!Objects.equals(jobId, id)) throw new IllegalStateException("EXPORT_ARTIFACT_NOT_OWNED");
            return root.resolve(jobId + ".zip");
        }
        @Override public synchronized void close() throws Exception {
            if (cleaned) return;
            closing = true;
            long end = System.nanoTime() + budgetNanos;
            try {
                while (!finished()) {
                    long left = end - System.nanoTime();
                    if (left <= 0) throw new IllegalStateException("EXPORT_CLOSE_WAIT_TIMED_OUT");
                    TimeUnit.NANOSECONDS.timedWait(this, left);
                }
            } catch (InterruptedException | RuntimeException failure) {
                // No premature deletion or config switch: completion of the SAME Runnable owns deferred cleanup.
                deferred = true;
                System.out.println("PLATFORM_EXPORT_DIRECTORY owner=" + owner + " state=RETAINED_WORKER_ACTIVE");
                throw failure;
            }
            if (cleanupFailure != null) throw cleanupFailure;
            cleanup();
        }
        private void cleanup() throws IOException {
            if (!finished()) throw new IllegalStateException("REFUSE_ACTIVE_EXPORT_CLEANUP");
            try {
                if (!Objects.equals(properties.getExportDirectory(), root)) throw new IOException("EXPORT_CONFIG_CHANGED_EXTERNALLY");
                var attrs = Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attrs.isDirectory() || !Objects.equals(directoryKey, attrs.fileKey()) || !createdAt.equals(attrs.creationTime())
                        || !root.toRealPath().equals(root) || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                        || !markerContents.equals(Files.readString(marker)))
                    throw new IOException("EXPORT_DIRECTORY_IDENTITY_CHANGED");
                try (var entries = Files.list(root)) {
                    for (Path entry : entries.toList()) {
                        if (entry.equals(marker)) continue;
                        if (jobId == null || !entry.equals(artifact(jobId)) || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS))
                            throw new IOException("UNKNOWN_EXPORT_DIRECTORY_ENTRY_RETAINED");
                    }
                }
                if (jobId != null) Files.deleteIfExists(artifact(jobId));
                Files.delete(marker);
                Files.delete(root); // Never recursive; an unexpected entry fails closed.
                cleaned = true;
                System.out.println("PLATFORM_EXPORT_DIRECTORY owner=" + owner + " state=CLEANED");
            } finally {
                if (Objects.equals(properties.getExportDirectory(), root)) properties.setExportDirectory(previous);
            }
        }
    }
}

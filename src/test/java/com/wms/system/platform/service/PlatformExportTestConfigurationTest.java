package com.wms.system.platform.service;

import com.wms.system.platform.config.PlatformAccessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class PlatformExportTestConfigurationTest {
    PlatformAccessProperties settings() { var p=new PlatformAccessProperties();p.setExportDirectory(Path.of("original-do-not-touch"));return p; }
    PlatformExportTestConfiguration.TrackingExecutor direct() { return new PlatformExportTestConfiguration.TrackingExecutor(Runnable::run); }
    @Test void namedRealExecutorIsWrappedInActualSpringInjectionChain() throws Exception {
        var ran=new AtomicBoolean();
        try(var context=new AnnotationConfigApplicationContext()) {
            context.register(PlatformExportTestConfiguration.class);
            context.registerBean("platformExportExecutor",Executor.class,()->command->{ran.set(true);command.run();});
            context.refresh();
            var executor=context.getBean("platformExportExecutor",PlatformExportTestConfiguration.TrackingExecutor.class);
            try(var lease=executor.begin(settings(),"owned:1",Duration.ofSeconds(1))) { executor.execute(()->{});lease.awaitWorker(); }
            assertThat(ran).isTrue();
        }
    }
    @Test void completedOwnedArtifactIsDeletedAndOriginalConfigRestored() throws Exception {
        var p=settings();var executor=direct();Path root;String id=UUID.randomUUID().toString();
        try(var lease=executor.begin(p,"owned:2",Duration.ofSeconds(1))) {
            root=lease.root;lease.bind(id);executor.execute(()->{});lease.awaitWorker();Files.writeString(lease.artifact(id),"synthetic");
        }
        assertThat(root).doesNotExist();assertThat(p.getExportDirectory()).isEqualTo(Path.of("original-do-not-touch"));
    }
    @Test void noSubmissionStillCleansEmptyOwnedDirectory() throws Exception {
        var p=settings();var lease=direct().begin(p,"owned:3",Duration.ofSeconds(1));lease.close();
        assertThat(lease.root).doesNotExist();assertThat(p.getExportDirectory()).isEqualTo(Path.of("original-do-not-touch"));
    }
    @Test void unregisteredSubmissionFails() { assertThatThrownBy(()->direct().execute(()->{})).hasMessage("EXPORT_WITHOUT_OWNED_DIRECTORY"); }
    @Test void duplicateSubmissionFailsWithoutRunningSecondTask() throws Exception {
        var executor=direct();try(var lease=executor.begin(settings(),"owned:4",Duration.ofSeconds(1))) {
            executor.execute(()->{});assertThatThrownBy(()->executor.execute(()->{throw new AssertionError();})).hasMessage("UNEXPECTED_EXPORT_SUBMISSION");
        }
    }
    @Test void foreignArtifactCannotBeClaimed() throws Exception {
        try(var lease=direct().begin(settings(),"owned:5",Duration.ofSeconds(1))) {
            lease.bind(UUID.randomUUID().toString());assertThatThrownBy(()->lease.artifact(UUID.randomUUID().toString())).hasMessage("EXPORT_ARTIFACT_NOT_OWNED");
        }
    }
    @Test void cleanupFailureIsSuppressedBehindOriginalAssertion() throws Exception {
        var p=settings();var lease=direct().begin(p,"owned:6",Duration.ofSeconds(1));Path unknown=lease.root.resolve("unknown.txt");Files.writeString(unknown,"test-owned-but-not-registered");
        try {
            Throwable error=catchThrowable(()->{try(lease){throw new AssertionError("PRIMARY_TEST_FAILURE");}});
            assertThat(error).isInstanceOf(AssertionError.class).hasMessage("PRIMARY_TEST_FAILURE");
            assertThat(error.getSuppressed()).hasSize(1);assertThat(error.getSuppressed()[0]).hasMessage("UNKNOWN_EXPORT_DIRECTORY_ENTRY_RETAINED");
            assertThat(unknown).exists();assertThat(p.getExportDirectory()).isEqualTo(Path.of("original-do-not-touch"));
        } finally { Files.delete(unknown);Files.delete(lease.marker);Files.delete(lease.root); }
    }
    @Test void unknownEntryPreventsDeletingEvenKnownArtifact() throws Exception {
        var lease=direct().begin(settings(),"owned:7",Duration.ofSeconds(1));String id=UUID.randomUUID().toString();lease.bind(id);
        Path own=lease.artifact(id),other=lease.root.resolve("other");Files.writeString(own,"own");Files.writeString(other,"other");
        try { assertThatThrownBy(lease::close).hasMessage("UNKNOWN_EXPORT_DIRECTORY_ENTRY_RETAINED");assertThat(own).exists();assertThat(other).exists(); }
        finally {Files.delete(own);Files.delete(other);Files.delete(lease.marker);Files.delete(lease.root);}
    }
    @Test void directoryInPlaceOfZipIsNeverRecursivelyDeleted() throws Exception {
        var lease=direct().begin(settings(),"owned:8",Duration.ofSeconds(1));String id=UUID.randomUUID().toString();lease.bind(id);Files.createDirectory(lease.artifact(id));
        try {assertThatThrownBy(lease::close).hasMessage("UNKNOWN_EXPORT_DIRECTORY_ENTRY_RETAINED");assertThat(lease.artifact(id)).isDirectory();}
        finally {Files.delete(lease.artifact(id));Files.delete(lease.marker);Files.delete(lease.root);}
    }
    @Test void realRunningTaskPreventsEarlyDeleteAndRestoresOnlyAfterCompletion() throws Exception {
        var pool=Executors.newSingleThreadExecutor();var executor=new PlatformExportTestConfiguration.TrackingExecutor(pool);
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);var p=settings();
        var lease=executor.begin(p,"owned:9",Duration.ofMillis(50));
        try {
            executor.execute(()->{started.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            assertThat(started.await(2,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(lease::awaitWorker).hasMessage("EXPORT_WORKER_WAIT_TIMED_OUT");
            assertThatThrownBy(lease::close).hasMessage("EXPORT_CLOSE_WAIT_TIMED_OUT");
            assertThat(lease.root).exists();assertThat(p.getExportDirectory()).isEqualTo(lease.root);
            assertThatThrownBy(executor::requireIdle).hasMessage("EXPORT_WORKER_STILL_ACTIVE");
        } finally {release.countDown();pool.shutdown();assertThat(pool.awaitTermination(3,TimeUnit.SECONDS)).isTrue();}
        assertThat(lease.root).doesNotExist();assertThat(p.getExportDirectory()).isEqualTo(Path.of("original-do-not-touch"));
    }
    @Test void executorRejectionAllowsSafeClose() throws Exception {
        var executor=new PlatformExportTestConfiguration.TrackingExecutor(task->{throw new RejectedExecutionException("synthetic");});var p=settings();
        var lease=executor.begin(p,"owned:10",Duration.ofSeconds(1));
        try(lease) {assertThatThrownBy(()->executor.execute(()->{})).isInstanceOf(RejectedExecutionException.class);}
        assertThat(lease.root).doesNotExist();assertThat(p.getExportDirectory()).isEqualTo(Path.of("original-do-not-touch"));
    }
    @Test void workerFailureStillMarksRunnableComplete() throws Exception {
        var executor=direct();try(var lease=executor.begin(settings(),"owned:11",Duration.ofSeconds(1))) {
            assertThatThrownBy(()->executor.execute(()->{throw new IllegalStateException("synthetic");})).hasMessage("synthetic");
            lease.awaitWorker();executor.requireIdle();
        }
    }
    @Test void unrelatedConfigChangeIsNotOverwritten() throws Exception {
        var p=settings();var lease=direct().begin(p,"owned:12",Duration.ofSeconds(1));p.setExportDirectory(Path.of("other-task-config"));
        try {assertThatThrownBy(lease::close).hasMessage("EXPORT_CONFIG_CHANGED_EXTERNALLY");assertThat(p.getExportDirectory()).isEqualTo(Path.of("other-task-config"));}
        finally {Files.delete(lease.marker);Files.delete(lease.root);}
    }
    @Test void bindingRequiresCanonicalUniquePublicId() throws Exception {
        try(var lease=direct().begin(settings(),"owned:13",Duration.ofSeconds(1))) {
            assertThatThrownBy(()->lease.bind("../outside")).isInstanceOf(IllegalArgumentException.class);
            lease.bind(UUID.randomUUID().toString());assertThatThrownBy(()->lease.bind(UUID.randomUUID().toString())).hasMessage("INVALID_EXPORT_JOB_BINDING");
        }
    }
    @Test void awaitWithoutSubmissionDoesNotPretendWorkerRan() throws Exception {
        try(var lease=direct().begin(settings(),"owned:14",Duration.ofSeconds(1))) {assertThatThrownBy(lease::awaitWorker).hasMessage("EXPORT_SUBMISSION_COUNT_MISMATCH");}
    }
    @Test void changedOwnershipMarkerRefusesCleanupAndRestoresConfig() throws Exception {
        var p=settings();var lease=direct().begin(p,"owned:15",Duration.ofSeconds(1));Files.writeString(lease.marker,"changed-by-other-owner");
        try {assertThatThrownBy(lease::close).hasMessage("EXPORT_DIRECTORY_IDENTITY_CHANGED");assertThat(lease.root).exists();assertThat(p.getExportDirectory()).isEqualTo(Path.of("original-do-not-touch"));}
        finally {Files.delete(lease.marker);Files.delete(lease.root);}
    }
    @Test void missingExplicitTempRootNeverFallsBackToGlobalTemp() {
        String previous=System.getProperty("p3.export.fixture-temp-root");
        try {System.clearProperty("p3.export.fixture-temp-root");assertThatThrownBy(()->direct().begin(settings(),"owned:16",Duration.ofSeconds(1))).hasMessage("EXPLICIT_EXPORT_FIXTURE_TEMP_ROOT_REQUIRED");}
        finally {if(previous!=null)System.setProperty("p3.export.fixture-temp-root",previous);}
    }
}

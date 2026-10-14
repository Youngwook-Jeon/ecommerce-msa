package com.project.young.productservice.application.service;

import com.project.young.productservice.application.exception.ProductSearchIndexOwnershipLostException;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildExecutionPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildWorkPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@ExtendWith(OutputCaptureExtension.class)
class ProductSearchIndexBuildExecutorTest {
    private final ProductSearchIndexBuildWorkPort store = mock(ProductSearchIndexBuildWorkPort.class);
    private final ProductSearchIndexBuildExecutionPort execution = mock(ProductSearchIndexBuildExecutionPort.class);
    private final ScheduledExecutorService heartbeat = mock(ScheduledExecutorService.class);
    private final ProductSearchIndexBuildExecutor worker = new ProductSearchIndexBuildExecutor(store, execution, heartbeat,
            Duration.ofSeconds(60), Duration.ofSeconds(10));

    @Test
    void poll_withoutQueuedJob_onlyRecoversExpiredWork() {
        when(store.claim(any())).thenReturn(Optional.empty());
        worker.runOnce();
        verify(store).recoverExpired();
        verifyNoInteractions(execution, heartbeat);
    }

    @Test
    void execute_withSuccessfulBatch_verifiesBeforeMarkingReady() {
        var work = claimed();
        doAnswer(invocation -> {
            invocation.<LongConsumer>getArgument(2).accept(1);
            return null;
        }).when(execution).load(eq(work), any(), any());
        when(execution.verify(eq(work), any())).thenReturn(1L);
        worker.runOnce();
        var order = inOrder(store, execution);
        order.verify(execution).create(work);
        order.verify(execution).load(eq(work), any(), any());
        order.verify(store).recordBatch(work, 1);
        order.verify(store).beginVerification(work);
        order.verify(execution).verify(eq(work), any());
        order.verify(store).succeed(work, 1);
        verify(store, never()).fail(any());
    }

    @Test
    void execute_whenIndexCreationFails_recordsFailureWithoutLoading() {
        var work = claimed();
        doThrow(new IllegalStateException("mapping mismatch")).when(execution).create(work);
        worker.runOnce();
        verify(store).fail(work);
        verify(execution, never()).load(any(), any(), any());
        verify(store, never()).succeed(any(), anyLong());
    }

    @Test
    void execute_afterOwnershipLoss_doesNotCreateOrChangeTerminalState() {
        var work = claimed();
        doThrow(new ProductSearchIndexOwnershipLostException()).when(store).renew(eq(work), any());
        worker.runOnce();
        verifyNoInteractions(execution);
        verify(store, never()).fail(any());
        verify(store, never()).succeed(any(), anyLong());
    }

    @Test
    void heartbeatFailure_stopsNextCheckpointWithoutSuccessOrFailureWrite(CapturedOutput output) {
        var work = claimed();
        AtomicReference<Runnable> task = new AtomicReference<>();
        when(heartbeat.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    task.set(invocation.getArgument(0));
                    return mock(ScheduledFuture.class);
                });
        doAnswer(invocation -> {
            doThrow(new IllegalStateException("DB unavailable")).when(store).renew(eq(work), any());
            task.get().run();
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(execution).load(eq(work), any(), any());
        worker.runOnce();
        verify(store, never()).fail(any());
        verify(store, never()).succeed(any(), anyLong());
        assertThat(output.getAll()).contains("phase=HEARTBEAT", "jobId=" + work.job().id(),
                "indexVersionId=" + work.version().id(), "index=" + work.version().indexName(),
                "IllegalStateException: DB unavailable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE_INDEX", "LOAD_PRODUCTS", "BEGIN_VERIFICATION", "VERIFY_INDEX", "COMPLETE_BUILD"})
    void execute_failure_logsPhaseIdentifiersAndCauseChain(String phase, CapturedOutput output) {
        var work = claimed();
        var failure = new IllegalStateException("integration operation failed", new IOException("connection lost"));
        switch (phase) {
            case "CREATE_INDEX" -> doThrow(failure).when(execution).create(work);
            case "LOAD_PRODUCTS" -> doThrow(failure).when(execution).load(eq(work), any(), any());
            case "BEGIN_VERIFICATION" -> doThrow(failure).when(store).beginVerification(work);
            case "VERIFY_INDEX" -> doThrow(failure).when(execution).verify(eq(work), any());
            case "COMPLETE_BUILD" -> doThrow(failure).when(store).succeed(eq(work), anyLong());
            default -> throw new IllegalArgumentException("Unexpected phase");
        }
        worker.runOnce();
        assertThat(output.getAll()).contains("Index build execution failed", "phase=" + phase,
                "jobId=" + work.job().id(), "indexVersionId=" + work.version().id(),
                "index=" + work.version().indexName(), "IllegalStateException: integration operation failed",
                "Caused by: java.io.IOException: connection lost");
        verify(store).fail(work);
    }

    @Test
    void execute_failureRecordingAlsoFails_logsBothCauses(CapturedOutput output) {
        var work = claimed();
        doThrow(new IllegalStateException("mapping mismatch")).when(execution).create(work);
        var writeFailure = new IllegalStateException("metadata unavailable");
        doThrow(writeFailure).when(store).fail(work);
        assertThatThrownBy(worker::runOnce).isSameAs(writeFailure);
        assertThat(output.getAll()).contains("phase=CREATE_INDEX", "mapping mismatch", "phase=RECORD_FAILURE",
                "metadata unavailable", "jobId=" + work.job().id(), "index=" + work.version().indexName());
    }

    private ProductSearchIndexBuildWorkPort.Work claimed() {
        var now = Instant.now();
        var version = ProductSearchIndexVersion.register(UUID.randomUUID(), 1, 1, "a".repeat(64), "admin", now).beginBuild(now);
        var job = ProductSearchIndexBuildJob.queue(UUID.randomUUID(), version.id(), "admin", "request-1", 500, now).start(1, now);
        var work = new ProductSearchIndexBuildWorkPort.Work(job, version, UUID.randomUUID());
        when(store.claim(any())).thenReturn(Optional.of(work));
        when(heartbeat.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(mock(ScheduledFuture.class));
        return work;
    }
}

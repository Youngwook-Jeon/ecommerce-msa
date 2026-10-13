package com.project.young.productservice.application.service;

import com.project.young.productservice.application.exception.ProductSearchIndexOwnershipLostException;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildExecutionPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildWorkPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;

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
    void heartbeatFailure_stopsNextCheckpointWithoutSuccessOrFailureWrite() {
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

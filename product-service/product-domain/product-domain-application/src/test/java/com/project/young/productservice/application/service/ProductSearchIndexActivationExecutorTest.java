package com.project.young.productservice.application.service;

import com.project.young.productservice.application.exception.ProductSearchIndexOwnershipLostException;
import com.project.young.productservice.application.port.output.ProductSearchIndexActivationWorkPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexAliasPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductSearchIndexActivationExecutorTest {
    private final ProductSearchIndexActivationWorkPort store = mock(ProductSearchIndexActivationWorkPort.class);
    private final ProductSearchIndexAliasPort aliases = mock(ProductSearchIndexAliasPort.class);
    private final ScheduledExecutorService heartbeat = mock(ScheduledExecutorService.class);
    private final ProductSearchIndexActivationExecutor worker = new ProductSearchIndexActivationExecutor(store, aliases, heartbeat,
            Duration.ofSeconds(60), Duration.ofSeconds(10));

    @Test
    void poll_withoutQueuedActivation_doesNotContactOpenSearch() {
        when(store.claim(any())).thenReturn(Optional.empty());
        worker.runOnce();
        verify(store).recoverExpired();
        verifyNoInteractions(aliases, heartbeat);
    }

    @Test
    void execute_success_persistsSwitchIntentBeforeOneAliasRequestAndVerifiesBeforeCommit() {
        var work = claimed();
        worker.runOnce();
        var order = inOrder(store, aliases);
        order.verify(aliases).verifyBeforeSwitch(work);
        order.verify(store).beginSwitch(work);
        order.verify(aliases).switchAlias(work);
        order.verify(aliases).verifyAfterSwitch(work);
        order.verify(store).succeed(work);
        verify(store, never()).failOrUnconfirm(any());
    }

    @Test
    void execute_preflightFailure_recordsFailureWithoutAliasRequest() {
        var work = claimed();
        doThrow(new IllegalStateException("alias mismatch")).when(aliases).verifyBeforeSwitch(work);
        worker.runOnce();
        verify(store).failOrUnconfirm(work);
        verify(aliases, never()).switchAlias(any());
    }

    @Test
    void execute_switchTimeout_recordsUncertaintyWithoutRetry() {
        var work = claimed();
        doThrow(new IllegalStateException("response timeout")).when(aliases).switchAlias(work);
        worker.runOnce();
        verify(aliases).switchAlias(work);
        verify(store).failOrUnconfirm(work);
        verify(store, never()).succeed(any());
    }

    @Test
    void execute_postflightMismatch_cannotMarkActive() {
        var work = claimed();
        doThrow(new IllegalStateException("multiple alias targets")).when(aliases).verifyAfterSwitch(work);
        worker.runOnce();
        verify(store).failOrUnconfirm(work);
        verify(store, never()).succeed(any());
    }

    @Test
    void execute_databaseCommitFailure_doesNotSwitchAgain() {
        var work = claimed();
        doThrow(new IllegalStateException("database unavailable")).when(store).succeed(work);
        worker.runOnce();
        verify(aliases).switchAlias(work);
        verify(store).failOrUnconfirm(work);
    }

    @Test
    void execute_lostOwnership_preventsAliasRequestAndFailureMutation() {
        var work = claimed();
        doThrow(new ProductSearchIndexOwnershipLostException()).when(store).renew(eq(work), any());
        worker.runOnce();
        verifyNoInteractions(aliases);
        verify(store, never()).failOrUnconfirm(any());
    }

    private ProductSearchIndexActivationWorkPort.Work claimed() {
        Instant now = Instant.now();
        var version = ProductSearchIndexVersion.register(UUID.randomUUID(), 1, 1, "a".repeat(64), "admin", now)
                .beginBuild(now).markReady(now).beginActivation(now);
        var job = ProductSearchIndexActivationJob.queue(UUID.randomUUID(), version.id(), null, "products-read", "admin", "activate-1", now).start(now);
        var work = new ProductSearchIndexActivationWorkPort.Work(job, version, null, UUID.randomUUID());
        when(store.claim(any())).thenReturn(Optional.of(work));
        when(heartbeat.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(mock(ScheduledFuture.class));
        return work;
    }
}

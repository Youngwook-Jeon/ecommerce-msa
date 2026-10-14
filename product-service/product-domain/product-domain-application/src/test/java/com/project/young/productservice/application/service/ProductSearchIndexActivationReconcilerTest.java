package com.project.young.productservice.application.service;

import com.project.young.productservice.application.port.output.ProductSearchIndexActivationWorkPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexAliasPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexReconciliationPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexAliasState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ProductSearchIndexActivationReconcilerTest {
    private final ProductSearchIndexReconciliationPort store = mock(ProductSearchIndexReconciliationPort.class);
    private final ProductSearchIndexActivationWorkPort activations = mock(ProductSearchIndexActivationWorkPort.class);
    private final ProductSearchIndexAliasPort aliases = mock(ProductSearchIndexAliasPort.class);
    private final ProductSearchIndexActivationReconciler reconciler = new ProductSearchIndexActivationReconciler(store, activations, aliases,
            Duration.ofSeconds(60), Duration.ofSeconds(60));

    @Test
    void automatic_noCandidate_onlyRecoversExpiredOwnership() {
        when(store.claimNext(any(), any())).thenReturn(Optional.empty());
        reconciler.runOnce();
        verify(activations).recoverExpired();
        verify(store).recoverExpired();
        verifyNoInteractions(aliases);
    }

    @Test
    void manual_observation_usesIndependentClaimAndNeverChangesAlias() {
        var work = work();
        var state = new ProductSearchIndexAliasState(Set.of("new"), true);
        when(store.claim(eq(work.job().id()), eq("admin"), any())).thenReturn(work);
        when(aliases.readAlias("products-read")).thenReturn(state);
        reconciler.recheck(work.job().id(), "admin");
        verify(store).finish(work, state);
        verify(aliases).readAlias("products-read");
        verifyNoMoreInteractions(aliases);
    }

    @Test
    void aliasLookupFailure_recordsUnknownInsteadOfEmptyAlias() {
        var work = work();
        when(store.claimNext(any(), any())).thenReturn(Optional.of(work));
        when(aliases.readAlias("products-read")).thenThrow(new IllegalStateException("connection lost"));
        reconciler.runOnce();
        verify(store).finish(eq(work), isNull());
        verify(aliases).readAlias("products-read");
        verifyNoMoreInteractions(aliases);
    }

    @Test
    void databaseFinishFailure_propagatesWithoutSecondObservationOrFailureWrite() {
        var work = work();
        var state = new ProductSearchIndexAliasState(Set.of("new"), true);
        when(store.claimNext(any(), any())).thenReturn(Optional.of(work));
        when(aliases.readAlias("products-read")).thenReturn(state);
        when(store.finish(work, state)).thenThrow(new IllegalStateException("database lost"));
        assertThatThrownBy(reconciler::runOnce).hasMessage("database lost");
        verify(store).finish(work, state);
        verify(aliases).readAlias("products-read");
        verifyNoMoreInteractions(aliases);
    }

    @Test
    void manual_invalidRequesterOrJob_doesNotStartAttempt() {
        assertThatThrownBy(() -> reconciler.recheck(null, "admin")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> reconciler.recheck(UUID.randomUUID(), " ")).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(store, aliases, activations);
    }

    private static ProductSearchIndexReconciliationPort.Work work() {
        Instant now = Instant.now();
        var job = ProductSearchIndexActivationJob.queue(UUID.randomUUID(), UUID.randomUUID(), null,
                "products-read", "admin", "activate-1", now).start(now).beginSwitch(now)
                .markUnconfirmed("UNKNOWN", "확인 필요", now);
        return new ProductSearchIndexReconciliationPort.Work(UUID.randomUUID(), job, 1, "new", null);
    }
}

package com.project.young.productservice.application.service;

import com.project.young.productservice.application.exception.ProductSearchIndexOwnershipLostException;
import com.project.young.productservice.application.port.output.ProductSearchIndexActivationWorkPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexAliasPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ProductSearchIndexActivationExecutor {
    private final ProductSearchIndexActivationWorkPort store;
    private final ProductSearchIndexAliasPort aliases;
    private final ScheduledExecutorService heartbeat;
    private final Duration lease;
    private final Duration interval;

    public ProductSearchIndexActivationExecutor(ProductSearchIndexActivationWorkPort store, ProductSearchIndexAliasPort aliases,
                                                ScheduledExecutorService heartbeat, Duration lease, Duration interval) {
        this.store = store;
        this.aliases = aliases;
        this.heartbeat = heartbeat;
        this.lease = lease;
        this.interval = interval;
    }

    private enum Phase {
        START_HEARTBEAT, VERIFY_BEFORE_SWITCH, BEGIN_SWITCH, SWITCH_ALIAS, VERIFY_AFTER_SWITCH, COMPLETE_ACTIVATION
    }

    public void runOnce() {
        store.recoverExpired();
        var candidate = store.claim(lease);
        if (candidate.isEmpty()) {
            return;
        }
        var work = candidate.get();
        var lost = new AtomicBoolean();
        ScheduledFuture<?> renewal = null;
        Phase phase = Phase.START_HEARTBEAT;
        Runnable checkpoint = () -> {
            if (lost.get() || Thread.currentThread().isInterrupted()) {
                throw new ProductSearchIndexOwnershipLostException();
            }
            store.renew(work, lease);
        };
        try {
            renewal = heartbeat.scheduleWithFixedDelay(() -> {
                try {
                    store.renew(work, lease);
                } catch (RuntimeException exception) {
                    lost.set(true);
                    log.warn("Activation heartbeat failed: jobId={}, indexVersionId={}, alias={}, phase=HEARTBEAT",
                            work.job().id(), work.version().id(), work.job().aliasName(), exception);
                }
            }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
            phase = Phase.VERIFY_BEFORE_SWITCH;
            checkpoint.run();
            aliases.verifyBeforeSwitch(work);
            checkpoint.run();
            phase = Phase.BEGIN_SWITCH;
            store.beginSwitch(work);
            checkpoint.run();
            phase = Phase.SWITCH_ALIAS;
            aliases.switchAlias(work);
            checkpoint.run();
            phase = Phase.VERIFY_AFTER_SWITCH;
            aliases.verifyAfterSwitch(work);
            checkpoint.run();
            phase = Phase.COMPLETE_ACTIVATION;
            store.succeed(work);
        } catch (ProductSearchIndexOwnershipLostException exception) {
            log.warn("Activation stopped after ownership loss: jobId={}, indexVersionId={}, alias={}, phase={}",
                    work.job().id(), work.version().id(), work.job().aliasName(), phase, exception);
        } catch (RuntimeException exception) {
            log.warn("Activation execution failed: jobId={}, indexVersionId={}, index={}, alias={}, phase={}",
                    work.job().id(), work.version().id(), work.version().indexName(), work.job().aliasName(), phase, exception);
            if (!lost.get()) {
                try {
                    store.failOrUnconfirm(work);
                } catch (ProductSearchIndexOwnershipLostException ownershipLost) {
                    log.warn("Activation failure no longer owned: jobId={}", work.job().id(), ownershipLost);
                } catch (RuntimeException recordingFailure) {
                    log.error("Activation failure could not be recorded: jobId={}, phase=RECORD_FAILURE", work.job().id(), recordingFailure);
                    throw recordingFailure;
                }
            }
        } finally {
            if (renewal != null) {
                renewal.cancel(false);
            }
        }
    }
}

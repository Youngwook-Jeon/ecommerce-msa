package com.project.young.productservice.application.service;

import com.project.young.productservice.application.exception.ProductSearchIndexOwnershipLostException;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildExecutionPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildWorkPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ProductSearchIndexBuildExecutor {
    private final ProductSearchIndexBuildWorkPort store;
    private final ProductSearchIndexBuildExecutionPort execution;
    private final ScheduledExecutorService heartbeat;
    private final Duration lease;
    private final Duration heartbeatInterval;

    public ProductSearchIndexBuildExecutor(ProductSearchIndexBuildWorkPort store, ProductSearchIndexBuildExecutionPort execution,
            ScheduledExecutorService heartbeat, Duration lease, Duration heartbeatInterval) {
        this.store = store;
        this.execution = execution;
        this.heartbeat = heartbeat;
        this.lease = lease;
        this.heartbeatInterval = heartbeatInterval;
    }

    public void runOnce() {
        store.recoverExpired();
        var candidate = store.claim(lease);
        if (candidate.isEmpty()) {
            return;
        }
        var work = candidate.get();
        var lost = new AtomicBoolean();
        var renewal = heartbeat.scheduleWithFixedDelay(() -> {
            try {
                store.renew(work, lease);
            } catch (RuntimeException exception) {
                lost.set(true);
                log.warn("Index build heartbeat failed: jobId={}, exceptionType={}", work.job().id(), exception.getClass().getSimpleName());
            }
        }, heartbeatInterval.toMillis(), heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);
        Runnable checkpoint = () -> {
            if (lost.get() || Thread.currentThread().isInterrupted()) {
                throw new ProductSearchIndexOwnershipLostException();
            }
            store.renew(work, lease);
        };
        try {
            checkpoint.run();
            execution.create(work);
            execution.load(work, checkpoint, products -> {
                checkpoint.run();
                store.recordBatch(work, products);
            });
            checkpoint.run();
            store.beginVerification(work);
            long count = execution.verify(work, checkpoint);
            checkpoint.run();
            store.succeed(work, count);
        } catch (ProductSearchIndexOwnershipLostException exception) {
            log.warn("Index build stopped after ownership loss: jobId={}", work.job().id());
        } catch (RuntimeException exception) {
            log.warn("Index build execution failed: jobId={}, indexVersionId={}, exceptionType={}",
                    work.job().id(), work.version().id(), exception.getClass().getSimpleName());
            if (!lost.get()) {
                try {
                    store.fail(work);
                } catch (ProductSearchIndexOwnershipLostException ownershipLost) {
                    log.warn("Failed build no longer owned: jobId={}", work.job().id());
                }
            }
        } finally {
            renewal.cancel(false);
        }
    }
}

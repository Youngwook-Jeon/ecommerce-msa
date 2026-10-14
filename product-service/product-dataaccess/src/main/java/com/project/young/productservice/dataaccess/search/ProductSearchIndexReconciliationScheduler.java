package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.application.service.ProductSearchIndexActivationReconciler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ProductSearchIndexReconciliationScheduler {
    private final ProductSearchIndexActivationReconciler worker;
    private final ExecutorService threads;
    private final AtomicBoolean running = new AtomicBoolean();

    public ProductSearchIndexReconciliationScheduler(ProductSearchIndexActivationReconciler worker, ExecutorService threads) {
        this.worker = worker;
        this.threads = threads;
    }

    @Scheduled(fixedDelayString = "${product-service.search.reconciliation.poll-interval-millis:30000}")
    public void poll() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            threads.submit(() -> {
                try {
                    worker.runOnce();
                } catch (RuntimeException exception) {
                    log.warn("Index reconciliation poll failed: exceptionType={}", exception.getClass().getSimpleName(), exception);
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException exception) {
            running.set(false);
            throw exception;
        }
    }
}

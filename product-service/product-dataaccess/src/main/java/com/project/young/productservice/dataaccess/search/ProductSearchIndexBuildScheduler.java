package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.application.service.ProductSearchIndexBuildExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ProductSearchIndexBuildScheduler {
    private final ProductSearchIndexBuildExecutor worker;
    private final ExecutorService threads;
    private final AtomicBoolean running = new AtomicBoolean();

    public ProductSearchIndexBuildScheduler(ProductSearchIndexBuildExecutor worker, ExecutorService threads) {
        this.worker = worker;
        this.threads = threads;
    }

    @Scheduled(fixedDelayString = "${product-service.search.worker.poll-interval-millis:2000}")
    public void poll() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            threads.submit(() -> {
                try {
                    worker.runOnce();
                } catch (RuntimeException exception) {
                    log.warn("Index build worker poll failed: exceptionType={}", exception.getClass().getSimpleName(), exception);
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

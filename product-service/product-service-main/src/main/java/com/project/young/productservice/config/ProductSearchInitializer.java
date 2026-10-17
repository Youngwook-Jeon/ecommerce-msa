package com.project.young.productservice.config;

import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexActivationCommand;
import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexBuildCommand;
import com.project.young.productservice.application.port.output.ProductSearchIndexMetadataPort;
import com.project.young.productservice.application.service.AdminProductSearchIndexActivationService;
import com.project.young.productservice.application.service.AdminProductSearchIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Runs the existing initial build and activation flow before application startup completes.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "product-service.search", name = {"enabled", "initial-load.enabled"},
        havingValue = "true", matchIfMissing = true)
public class ProductSearchInitializer implements ApplicationRunner {
    private static final String REQUESTED_BY = "system-initial-load";
    private final ProductSearchIndexMetadataPort metadata;
    private final AdminProductSearchIndexService builds;
    private final AdminProductSearchIndexActivationService activations;
    private final int batchSize;
    private final long timeoutMillis;

    public ProductSearchInitializer(ProductSearchIndexMetadataPort metadata,
                                    AdminProductSearchIndexService builds,
                                    AdminProductSearchIndexActivationService activations,
                                    @Value("${product-service.search.initial-load.batch-size:500}") int batchSize,
                                    @Value("${product-service.search.initial-load.timeout-millis:300000}") long timeoutMillis,
                                    @Value("${product-service.search.worker.enabled:true}") boolean workerEnabled) {
        if (!workerEnabled || batchSize < 1 || batchSize > 1000 || timeoutMillis < 1) {
            throw new IllegalArgumentException("Search initial load requires enabled workers, batch size 1..1000 and a positive timeout");
        }
        this.metadata = metadata;
        this.builds = builds;
        this.activations = activations;
        this.batchSize = batchSize;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        var active = metadata.findActiveVersion();
        if (active.isPresent()) {
            log.info("Search initial load skipped: activeVersionId={}, index={}", active.get().id(), active.get().indexName());
            return;
        }
        long started = System.nanoTime();
        var build = builds.register(new RegisterProductSearchIndexBuildCommand(
                REQUESTED_BY, "initial-load", batchSize, true));
        log.info("Search initial load started: buildJobId={}, indexVersionId={}", build.id(), build.indexVersionId());
        await(() -> buildCompleted(build.id()), started, "build", build.id());
        var activation = activations.register(new RegisterProductSearchIndexActivationCommand(
                build.indexVersionId(), null, REQUESTED_BY, "initial-load-" + build.indexVersionId(), true));
        await(() -> activationCompleted(activation.id()), started, "activation", activation.id());
        log.info("Search initial load completed: buildJobId={}, activationJobId={}, indexVersionId={}",
                build.id(), activation.id(), build.indexVersionId());
    }

    private boolean buildCompleted(UUID id) {
        var job = builds.getJob(id);
        return switch (job.status()) {
            case SUCCEEDED -> true;
            case QUEUED, RUNNING, VERIFYING -> false;
            case FAILED, INTERRUPTED -> throw new IllegalStateException("Search initial build failed: jobId=" + id
                    + ", failureCode=" + job.failureCode() + ", failureMessage=" + job.failureMessage());
        };
    }

    private boolean activationCompleted(UUID id) {
        var job = activations.getJob(id);
        return switch (job.status()) {
            case SUCCEEDED -> true;
            case QUEUED, RUNNING, SWITCHING -> false;
            case FAILED, UNCONFIRMED -> throw new IllegalStateException("Search initial activation failed: jobId=" + id
                    + ", status=" + job.status() + ", failureCode=" + job.failureCode()
                    + ", failureMessage=" + job.failureMessage());
        };
    }

    private void await(BooleanSupplier completed, long started, String phase, UUID jobId) {
        while (!completed.getAsBoolean()) {
            long remaining = timeoutMillis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            if (remaining <= 0) {
                throw new IllegalStateException("Search initial load timed out: phase=" + phase + ", jobId=" + jobId);
            }
            try {
                Thread.sleep(Math.min(200, remaining));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Search initial load interrupted: phase=" + phase + ", jobId=" + jobId, exception);
            }
        }
    }
}

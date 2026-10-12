package com.project.young.productservice.application.searchindex.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One build attempt per index version; retries after a terminal failure create a new version/job. */
public record ProductSearchIndexBuildJob(UUID id, UUID indexVersionId, String requestedBy, String idempotencyKey,
                                        int batchSize, ProductSearchIndexBuildStatus status,
                                        Long sourceProductCount, long indexedProducts, long completedBatches,
                                        Instant createdAt, Instant updatedAt, Instant startedAt, Instant finishedAt,
                                        String failureCode, String failureMessage) {

    public ProductSearchIndexBuildJob {
        validateIdentity(id, indexVersionId, requestedBy, idempotencyKey);
        validateProgress(batchSize, sourceProductCount, indexedProducts, completedBatches);
        validateTimeline(createdAt, updatedAt, startedAt, finishedAt);
        validateState(status, sourceProductCount, indexedProducts, completedBatches, startedAt, finishedAt);
        validateFailure(status, failureCode, failureMessage);
    }

    private static void validateIdentity(UUID id, UUID indexVersionId, String requestedBy, String idempotencyKey) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(indexVersionId, "indexVersionId is required");
        if (requestedBy == null || requestedBy.isBlank() || requestedBy.length() > 255) {
            throw new IllegalArgumentException("requestedBy must be nonblank and at most 255 characters");
        }
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("idempotencyKey must contain 1 to 128 permitted ASCII characters");
        }
    }

    private static void validateProgress(int batchSize, Long sourceProductCount,
                                         long indexedProducts, long completedBatches) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        if (indexedProducts < 0) {
            throw new IllegalArgumentException("indexedProducts must be nonnegative");
        }
        if (completedBatches < 0) {
            throw new IllegalArgumentException("completedBatches must be nonnegative");
        }
        if (sourceProductCount != null) {
            if (sourceProductCount < 0) {
                throw new IllegalArgumentException("sourceProductCount must be nonnegative");
            }
            if (indexedProducts > sourceProductCount) {
                throw new IllegalArgumentException("indexedProducts must not exceed sourceProductCount");
            }
        }
    }

    private static void validateTimeline(Instant createdAt, Instant updatedAt, Instant startedAt, Instant finishedAt) {
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
        if (startedAt != null && (startedAt.isBefore(createdAt) || startedAt.isAfter(updatedAt))) {
            throw new IllegalArgumentException("startedAt must be between createdAt and updatedAt");
        }
        if (finishedAt != null && (finishedAt.isBefore(createdAt) || finishedAt.isAfter(updatedAt))) {
            throw new IllegalArgumentException("finishedAt must be between createdAt and updatedAt");
        }
    }

    private static void validateState(ProductSearchIndexBuildStatus status, Long sourceProductCount,
                                      long indexedProducts, long completedBatches,
                                      Instant startedAt, Instant finishedAt) {
        Objects.requireNonNull(status, "status is required");
        switch (status) {
            case QUEUED -> {
                if (startedAt != null || sourceProductCount != null) {
                    throw new IllegalArgumentException("QUEUED jobs must not have startedAt or sourceProductCount");
                }
                if (indexedProducts != 0 || completedBatches != 0) {
                    throw new IllegalArgumentException("QUEUED jobs must have zero progress");
                }
                validateCompletionTime(finishedAt, false);
            }
            case RUNNING, VERIFYING -> {
                validateStartedJob(sourceProductCount, startedAt);
                validateCompletionTime(finishedAt, false);
            }
            case SUCCEEDED -> {
                validateStartedJob(sourceProductCount, startedAt);
                validateCompletionTime(finishedAt, true);
                if (indexedProducts != sourceProductCount) {
                    throw new IllegalArgumentException("SUCCEEDED jobs must have indexed all source products");
                }
            }
            case FAILED, INTERRUPTED -> validateCompletionTime(finishedAt, true);
        }
    }

    private static void validateStartedJob(Long sourceProductCount, Instant startedAt) {
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt is required for RUNNING, VERIFYING and SUCCEEDED jobs");
        }
        if (sourceProductCount == null) {
            throw new IllegalArgumentException("sourceProductCount is required for RUNNING, VERIFYING and SUCCEEDED jobs");
        }
    }

    private static void validateCompletionTime(Instant finishedAt, boolean terminal) {
        if (terminal && finishedAt == null) {
            throw new IllegalArgumentException("finishedAt is required for terminal jobs");
        }
        if (!terminal && finishedAt != null) {
            throw new IllegalArgumentException("finishedAt must be absent for nonterminal jobs");
        }
    }

    private static void validateFailure(ProductSearchIndexBuildStatus status, String failureCode, String failureMessage) {
        switch (status) {
            case FAILED, INTERRUPTED -> {
                if (failureCode == null || !failureCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
                    throw new IllegalArgumentException("failureCode is required and must be an uppercase code of at most 64 characters");
                }
                if (failureMessage == null || failureMessage.isBlank() || failureMessage.length() > 500) {
                    throw new IllegalArgumentException("failureMessage must be nonblank and at most 500 characters for failed jobs");
                }
            }
            case QUEUED, RUNNING, VERIFYING, SUCCEEDED -> {
                if (failureCode != null || failureMessage != null) {
                    throw new IllegalArgumentException("failure details must be absent unless the job failed or was interrupted");
                }
            }
        }
    }

    public static ProductSearchIndexBuildJob queue(UUID id, UUID indexVersionId, String requestedBy,
                                                   String idempotencyKey, int batchSize, Instant now) {
        return new ProductSearchIndexBuildJob(id, indexVersionId, requestedBy, idempotencyKey, batchSize,
                ProductSearchIndexBuildStatus.QUEUED, null, 0, 0, now, now, null, null, null, null);
    }

    public ProductSearchIndexBuildJob start(long sourceCount, Instant now) {
        requireStatus(ProductSearchIndexBuildStatus.QUEUED);
        return copy(ProductSearchIndexBuildStatus.RUNNING, sourceCount, 0, 0, now, now, null, null, null);
    }

    public ProductSearchIndexBuildJob recordCompletedBatch(long products, Instant now) {
        requireStatus(ProductSearchIndexBuildStatus.RUNNING);
        if (products < 1 || products > batchSize) {
            throw new IllegalArgumentException("Completed batch size must be between 1 and configured batchSize");
        }
        return copy(status, sourceProductCount, Math.addExact(indexedProducts, products),
                Math.addExact(completedBatches, 1), now, startedAt, null, null, null);
    }

    public ProductSearchIndexBuildJob beginVerification(Instant now) {
        requireStatus(ProductSearchIndexBuildStatus.RUNNING);
        return copy(ProductSearchIndexBuildStatus.VERIFYING, sourceProductCount, indexedProducts,
                completedBatches, now, startedAt, null, null, null);
    }

    /** Counts are validated here; representative search checks must also pass before this call. */
    public ProductSearchIndexBuildJob succeed(long sourceCountAfter, long indexCount, Instant now) {
        requireStatus(ProductSearchIndexBuildStatus.VERIFYING);
        if (sourceCountAfter != sourceProductCount || indexCount != indexedProducts || indexCount != sourceProductCount) {
            throw new IllegalStateException("Index build counts do not match");
        }
        return copy(ProductSearchIndexBuildStatus.SUCCEEDED, sourceProductCount, indexedProducts,
                completedBatches, now, startedAt, now, null, null);
    }

    public ProductSearchIndexBuildJob fail(String code, String safeMessage, Instant now) {
        requireNonterminal();
        return copy(ProductSearchIndexBuildStatus.FAILED, sourceProductCount, indexedProducts,
                completedBatches, now, startedAt, now, code, safeMessage);
    }

    public ProductSearchIndexBuildJob interrupt(String safeMessage, Instant now) {
        if (status != ProductSearchIndexBuildStatus.RUNNING && status != ProductSearchIndexBuildStatus.VERIFYING) {
            throw new IllegalStateException("Only a claimed job can be interrupted");
        }
        return copy(ProductSearchIndexBuildStatus.INTERRUPTED, sourceProductCount, indexedProducts,
                completedBatches, now, startedAt, now, "WORKER_INTERRUPTED", safeMessage);
    }

    private void requireNonterminal() {
        if (status == ProductSearchIndexBuildStatus.SUCCEEDED || status == ProductSearchIndexBuildStatus.FAILED
                || status == ProductSearchIndexBuildStatus.INTERRUPTED) {
            throw new IllegalStateException("Terminal build jobs cannot change state");
        }
    }

    private void requireStatus(ProductSearchIndexBuildStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Invalid build job state transition");
        }
    }

    private ProductSearchIndexBuildJob copy(ProductSearchIndexBuildStatus next, Long sourceCount, long products,
                                            long batches, Instant now, Instant start, Instant finish,
                                            String code, String message) {
        if (now == null || now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("State transition time must not move backwards");
        }
        return new ProductSearchIndexBuildJob(id, indexVersionId, requestedBy, idempotencyKey, batchSize, next,
                sourceCount, products, batches, createdAt, now, start, finish, code, message);
    }
}

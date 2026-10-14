package com.project.young.productservice.application.searchindex.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable alias-switch intent. An uncertain switch remains unfinished until its result is confirmed. */
public record ProductSearchIndexActivationJob(
        UUID id, UUID indexVersionId, UUID expectedActiveVersionId, String aliasName,
        String requestedBy, String idempotencyKey, ProductSearchIndexActivationStatus status,
        Instant createdAt, Instant updatedAt, Instant startedAt, Instant switchStartedAt, Instant finishedAt,
        String failureCode, String failureMessage
) {
    public ProductSearchIndexActivationJob {
        validateIdentity(id, indexVersionId, expectedActiveVersionId, aliasName, requestedBy, idempotencyKey);
        validateTimeline(createdAt, updatedAt, startedAt, switchStartedAt, finishedAt);
        validateState(status, startedAt, switchStartedAt, finishedAt);
        validateFailure(status, failureCode, failureMessage);
    }

    private static void validateIdentity(UUID id, UUID indexVersionId, UUID expectedActiveVersionId,
                                         String aliasName, String requestedBy, String idempotencyKey) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(indexVersionId, "indexVersionId is required");
        if (indexVersionId.equals(expectedActiveVersionId)) {
            throw new IllegalArgumentException("Target version must differ from expected active version");
        }
        if (aliasName == null || !aliasName.matches("[a-z0-9][a-z0-9._-]{0,254}")) {
            throw new IllegalArgumentException("aliasName must be an explicit alias name of at most 255 characters");
        }
        if (requestedBy == null || requestedBy.isBlank() || requestedBy.length() > 255) {
            throw new IllegalArgumentException("requestedBy must be nonblank and at most 255 characters");
        }
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("idempotencyKey must contain 1 to 128 permitted ASCII characters");
        }
    }

    private static void validateTimeline(Instant createdAt, Instant updatedAt, Instant startedAt,
                                         Instant switchStartedAt, Instant finishedAt) {
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
        validateTimestamp("startedAt", startedAt, createdAt, updatedAt);
        validateTimestamp("switchStartedAt", switchStartedAt, startedAt == null ? createdAt : startedAt, updatedAt);
        Instant completionFloor = switchStartedAt != null ? switchStartedAt : startedAt != null ? startedAt : createdAt;
        validateTimestamp("finishedAt", finishedAt, completionFloor, updatedAt);
    }

    private static void validateTimestamp(String name, Instant value, Instant earliest, Instant latest) {
        if (value != null && (value.isBefore(earliest) || value.isAfter(latest))) {
            throw new IllegalArgumentException(name + " must follow previous phases and not exceed updatedAt");
        }
    }

    private static void validateState(ProductSearchIndexActivationStatus status, Instant startedAt,
                                      Instant switchStartedAt, Instant finishedAt) {
        Objects.requireNonNull(status, "status is required");
        switch (status) {
            case QUEUED -> {
                if (startedAt != null || switchStartedAt != null || finishedAt != null) {
                    throw new IllegalArgumentException("QUEUED activations must not have execution timestamps");
                }
            }
            case RUNNING -> {
                requireStarted(startedAt);
                if (switchStartedAt != null || finishedAt != null) {
                    throw new IllegalArgumentException("RUNNING activations must not have switch or completion timestamps");
                }
            }
            case SWITCHING, UNCONFIRMED -> {
                requireSwitchStarted(startedAt, switchStartedAt);
                if (finishedAt != null) {
                    throw new IllegalArgumentException("Unresolved activations must not have finishedAt");
                }
            }
            case SUCCEEDED -> {
                requireSwitchStarted(startedAt, switchStartedAt);
                requireFinished(finishedAt);
            }
            case FAILED -> {
                if (switchStartedAt != null) {
                    throw new IllegalArgumentException("FAILED activations must not have attempted an alias switch");
                }
                requireFinished(finishedAt);
            }
        }
    }

    private static void requireStarted(Instant startedAt) {
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt is required for claimed activations");
        }
    }

    private static void requireSwitchStarted(Instant startedAt, Instant switchStartedAt) {
        requireStarted(startedAt);
        if (switchStartedAt == null) {
            throw new IllegalArgumentException("switchStartedAt is required once an alias switch may have been attempted");
        }
    }

    private static void requireFinished(Instant finishedAt) {
        if (finishedAt == null) {
            throw new IllegalArgumentException("finishedAt is required for terminal activations");
        }
    }

    private static void validateFailure(ProductSearchIndexActivationStatus status, String code, String message) {
        switch (status) {
            case FAILED, UNCONFIRMED -> {
                if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}")) {
                    throw new IllegalArgumentException("failureCode must be an uppercase code of at most 64 characters");
                }
                if (message == null || message.isBlank() || message.length() > 500) {
                    throw new IllegalArgumentException("failureMessage must be nonblank and at most 500 characters");
                }
            }
            case QUEUED, RUNNING, SWITCHING, SUCCEEDED -> {
                if (code != null || message != null) {
                    throw new IllegalArgumentException("Failure details are only allowed for FAILED or UNCONFIRMED activations");
                }
            }
        }
    }

    /** A null expectedActiveVersionId means the alias must currently have no active version. */
    public static ProductSearchIndexActivationJob queue(UUID id, UUID targetVersionId, UUID expectedActiveVersionId,
                                                        String aliasName, String requestedBy, String idempotencyKey, Instant now) {
        return new ProductSearchIndexActivationJob(id, targetVersionId, expectedActiveVersionId, aliasName,
                requestedBy, idempotencyKey, ProductSearchIndexActivationStatus.QUEUED,
                now, now, null, null, null, null, null);
    }

    public ProductSearchIndexActivationJob start(Instant now) {
        requireStatus(ProductSearchIndexActivationStatus.QUEUED);
        return copy(ProductSearchIndexActivationStatus.RUNNING, now, now, null, null, null, null);
    }

    /** Persist this transition before submitting any alias-changing request. */
    public ProductSearchIndexActivationJob beginSwitch(Instant now) {
        requireStatus(ProductSearchIndexActivationStatus.RUNNING);
        return copy(ProductSearchIndexActivationStatus.SWITCHING, now, startedAt, now, null, null, null);
    }

    /** Call only after observing that the alias points exclusively to the target version. */
    public ProductSearchIndexActivationJob confirmActivation(UUID actualActiveVersionId, Instant now) {
        requireSwitchPending();
        if (!indexVersionId.equals(actualActiveVersionId)) {
            throw new IllegalStateException("Alias must point exclusively to the target version before success");
        }
        return copy(ProductSearchIndexActivationStatus.SUCCEEDED, now, startedAt, switchStartedAt, now, null, null);
    }

    public ProductSearchIndexActivationJob markUnconfirmed(String code, String safeMessage, Instant now) {
        requireStatus(ProductSearchIndexActivationStatus.SWITCHING);
        return copy(ProductSearchIndexActivationStatus.UNCONFIRMED, now, startedAt, switchStartedAt, null, code, safeMessage);
    }

    public ProductSearchIndexActivationJob failBeforeSwitch(String code, String safeMessage, Instant now) {
        if (status != ProductSearchIndexActivationStatus.QUEUED && status != ProductSearchIndexActivationStatus.RUNNING) {
            throw new IllegalStateException("Only an activation that has not attempted a switch can fail directly");
        }
        return copy(ProductSearchIndexActivationStatus.FAILED, now, startedAt, null, now, code, safeMessage);
    }

    private void requireStatus(ProductSearchIndexActivationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Invalid activation job state transition");
        }
    }

    private void requireSwitchPending() {
        if (status != ProductSearchIndexActivationStatus.SWITCHING && status != ProductSearchIndexActivationStatus.UNCONFIRMED) {
            throw new IllegalStateException("Only a pending alias switch can be confirmed");
        }
    }

    private ProductSearchIndexActivationJob copy(ProductSearchIndexActivationStatus next, Instant now,
                                                 Instant start, Instant switching, Instant finish, String code, String message) {
        if (now == null || now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("State transition time must not move backwards");
        }
        return new ProductSearchIndexActivationJob(id, indexVersionId, expectedActiveVersionId, aliasName,
                requestedBy, idempotencyKey, next, createdAt, now, start, switching, finish, code, message);
    }
}

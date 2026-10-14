package com.project.young.productservice.application.searchindex.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Each automatic/manual observation is audited independently of the activation worker's ownership. */
public record ProductSearchIndexReconciliationAttempt(UUID id, UUID jobId, String requestedBy,
        ProductSearchIndexReconciliationStatus status, Instant createdAt, Instant leaseUntil, Instant finishedAt,
        ProductSearchIndexAliasObservation observation, List<String> observedIndices, Boolean aliasPlain,
        String failureCode, String failureMessage) {
    public ProductSearchIndexReconciliationAttempt {
        validateIdentity(id, jobId, requestedBy);
        validateTimeline(createdAt, leaseUntil, finishedAt);
        observedIndices = validateObservation(observation, observedIndices, aliasPlain);
        validateState(status, finishedAt, observation);
        validateFailure(status, failureCode, failureMessage);
    }

    private static void validateIdentity(UUID id, UUID jobId, String requestedBy) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(jobId, "jobId is required");
        if (requestedBy != null && (requestedBy.isBlank() || requestedBy.length() > 255)) {
            throw new IllegalArgumentException("Manual requester must be nonblank and at most 255 characters");
        }
    }

    private static void validateTimeline(Instant createdAt, Instant leaseUntil, Instant finishedAt) {
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(leaseUntil, "leaseUntil is required");
        if (!leaseUntil.isAfter(createdAt) || (finishedAt != null && finishedAt.isBefore(createdAt))) {
            throw new IllegalArgumentException("Reconciliation timestamps must follow creation");
        }
    }

    private static List<String> validateObservation(ProductSearchIndexAliasObservation observation,
                                                    List<String> observedIndices, Boolean aliasPlain) {
        boolean lookupFailed = observation == ProductSearchIndexAliasObservation.LOOKUP_FAILED;
        if (observation == null || lookupFailed) {
            if (observedIndices != null || aliasPlain != null) {
                throw new IllegalArgumentException("Unknown alias state must not contain observed values");
            }
        } else {
            observedIndices = List.copyOf(Objects.requireNonNull(observedIndices, "observedIndices are required"));
            Objects.requireNonNull(aliasPlain, "aliasPlain is required");
        }
        return observedIndices;
    }

    private static void validateState(ProductSearchIndexReconciliationStatus status, Instant finishedAt,
                                      ProductSearchIndexAliasObservation observation) {
        Objects.requireNonNull(status, "status is required");
        if ((status == ProductSearchIndexReconciliationStatus.RUNNING) != (finishedAt == null)) {
            throw new IllegalArgumentException("Only running reconciliation attempts must have no finishedAt");
        }
        boolean lookupFailed = observation == ProductSearchIndexAliasObservation.LOOKUP_FAILED;
        switch (status) {
            case RUNNING, EXPIRED -> {
                if (observation != null) {
                    throw new IllegalArgumentException("Unobserved attempts must not contain an observation");
                }
            }
            case SUCCEEDED -> {
                if (observation != ProductSearchIndexAliasObservation.TARGET_CONFIRMED) {
                    throw new IllegalArgumentException("Successful reconciliation must confirm the target");
                }
            }
            case UNRESOLVED -> {
                if (observation == null || lookupFailed || observation == ProductSearchIndexAliasObservation.TARGET_CONFIRMED) {
                    throw new IllegalArgumentException("Unresolved attempts require a non-target alias observation");
                }
            }
            case FAILED -> {
                if (!lookupFailed) {
                    throw new IllegalArgumentException("Failed observations must report lookup failure");
                }
            }
            case STALE -> { }
        }
    }

    private static void validateFailure(ProductSearchIndexReconciliationStatus status, String failureCode, String failureMessage) {
        boolean diagnostic = status == ProductSearchIndexReconciliationStatus.FAILED
                || status == ProductSearchIndexReconciliationStatus.STALE || status == ProductSearchIndexReconciliationStatus.EXPIRED;
        if (diagnostic) {
            if (failureCode == null || !failureCode.matches("[A-Z][A-Z0-9_]{0,63}") || failureMessage == null
                    || failureMessage.isBlank() || failureMessage.length() > 500) {
                throw new IllegalArgumentException("Safe reconciliation failure details are required");
            }
        } else if (failureCode != null || failureMessage != null) {
            throw new IllegalArgumentException("Failure details are only allowed for failed, stale or expired attempts");
        }
    }
}

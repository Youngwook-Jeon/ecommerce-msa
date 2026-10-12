package com.project.young.productservice.application.searchindex.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Metadata for a physical search index. ACTIVE is confirmed from the actual read alias. */
public record ProductSearchIndexVersion(UUID id, long versionNumber, String indexName, int mappingVersion,
                                        String mappingHash, ProductSearchIndexStatus status, String requestedBy,
                                        Instant createdAt, Instant updatedAt, Instant activatedAt) {

    public ProductSearchIndexVersion {
        validateIdentity(id, versionNumber, requestedBy);
        validateMapping(indexName, mappingVersion, mappingHash);
        validateTimeline(createdAt, updatedAt, activatedAt);
        validateState(status, activatedAt);
    }

    private static void validateIdentity(UUID id, long versionNumber, String requestedBy) {
        Objects.requireNonNull(id, "id is required");
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber must be positive");
        }
        if (requestedBy == null || requestedBy.isBlank() || requestedBy.length() > 255) {
            throw new IllegalArgumentException("requestedBy must be nonblank and at most 255 characters");
        }
    }

    private static void validateMapping(String indexName, int mappingVersion, String mappingHash) {
        if (indexName == null || !indexName.matches("[a-z0-9][a-z0-9._-]{0,254}")) {
            throw new IllegalArgumentException("indexName must be a physical index name of at most 255 characters");
        }
        if (mappingVersion < 1) {
            throw new IllegalArgumentException("mappingVersion must be positive");
        }
        if (mappingHash == null || !mappingHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("mappingHash must be a lowercase SHA-256 hex string");
        }
    }

    private static void validateTimeline(Instant createdAt, Instant updatedAt, Instant activatedAt) {
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
        if (activatedAt != null && (activatedAt.isBefore(createdAt) || activatedAt.isAfter(updatedAt))) {
            throw new IllegalArgumentException("activatedAt must be between createdAt and updatedAt");
        }
    }

    private static void validateState(ProductSearchIndexStatus status, Instant activatedAt) {
        Objects.requireNonNull(status, "status is required");
        switch (status) {
            case ACTIVE -> {
                if (activatedAt == null) {
                    throw new IllegalArgumentException("activatedAt is required for ACTIVE indexes");
                }
            }
            case REGISTERED, BUILDING, READY, ACTIVATING, INACTIVE, FAILED -> {
                // Other states do not require an activation timestamp; retain one when present.
            }
        }
    }

    public static ProductSearchIndexVersion register(UUID id, long versionNumber, int mappingVersion,
                                                     String mappingHash, String requestedBy, Instant now) {
        return new ProductSearchIndexVersion(id, versionNumber, "products-v" + versionNumber, mappingVersion,
                mappingHash, ProductSearchIndexStatus.REGISTERED, requestedBy, now, now, null);
    }

    public ProductSearchIndexVersion beginBuild(Instant now) {
        return transition(ProductSearchIndexStatus.REGISTERED, ProductSearchIndexStatus.BUILDING, now, activatedAt);
    }

    public ProductSearchIndexVersion markReady(Instant now) {
        return transition(ProductSearchIndexStatus.BUILDING, ProductSearchIndexStatus.READY, now, activatedAt);
    }

    public ProductSearchIndexVersion beginActivation(Instant now) {
        if (status != ProductSearchIndexStatus.READY && status != ProductSearchIndexStatus.INACTIVE) {
            throw new IllegalStateException("Only a validated index can be activated");
        }
        return transition(status, ProductSearchIndexStatus.ACTIVATING, now, activatedAt);
    }

    /** Call only after verifying that the read alias points exclusively to this index. */
    public ProductSearchIndexVersion confirmActive(Instant now) {
        return transition(ProductSearchIndexStatus.ACTIVATING, ProductSearchIndexStatus.ACTIVE, now, now);
    }

    public ProductSearchIndexVersion deactivate(Instant now) {
        return transition(ProductSearchIndexStatus.ACTIVE, ProductSearchIndexStatus.INACTIVE, now, activatedAt);
    }

    public ProductSearchIndexVersion failBuild(Instant now) {
        if (status != ProductSearchIndexStatus.REGISTERED && status != ProductSearchIndexStatus.BUILDING) {
            throw new IllegalStateException("Only an unfinished index build can fail");
        }
        return transition(status, ProductSearchIndexStatus.FAILED, now, activatedAt);
    }

    private ProductSearchIndexVersion transition(ProductSearchIndexStatus expected, ProductSearchIndexStatus next,
                                                 Instant now, Instant activationTime) {
        if (status != expected) {
            throw new IllegalStateException("Invalid search index state transition");
        }
        if (now == null || now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("State transition time must not move backwards");
        }
        return new ProductSearchIndexVersion(id, versionNumber, indexName, mappingVersion, mappingHash, next,
                requestedBy, createdAt, now, activationTime);
    }
}

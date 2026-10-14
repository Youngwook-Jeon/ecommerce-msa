package com.project.young.productservice.application.searchindex.model;

import java.util.Objects;
import java.util.Set;

/** Provider-independent snapshot and matching rules for the managed read alias. */
public record ProductSearchIndexAliasState(Set<String> indices, boolean plain) {
    public ProductSearchIndexAliasState {
        indices = Set.copyOf(Objects.requireNonNull(indices, "indices are required"));
        if (indices.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Observed index names must be nonblank");
        }
    }

    /** A null expectedIndex denotes an absent alias, not a skipped comparison. */
    public boolean matches(String expectedIndex) {
        return plain && indices.equals(expectedIndex == null ? Set.of() : Set.of(expectedIndex));
    }

    public ProductSearchIndexAliasObservation classify(String targetIndex, String previousIndex) {
        if (targetIndex == null || targetIndex.isBlank()) {
            throw new IllegalArgumentException("Target index is required");
        }
        if (!plain) {
            return ProductSearchIndexAliasObservation.ALIAS_OPTIONS_MISMATCH;
        }
        if (indices.isEmpty()) {
            return ProductSearchIndexAliasObservation.ALIAS_ABSENT;
        }
        if (indices.size() > 1) {
            return ProductSearchIndexAliasObservation.MULTIPLE_INDICES;
        }
        if (matches(targetIndex)) {
            return ProductSearchIndexAliasObservation.TARGET_CONFIRMED;
        }
        if (previousIndex != null && matches(previousIndex)) {
            return ProductSearchIndexAliasObservation.PREVIOUS_OBSERVED;
        }
        return ProductSearchIndexAliasObservation.UNEXPECTED_INDEX;
    }
}

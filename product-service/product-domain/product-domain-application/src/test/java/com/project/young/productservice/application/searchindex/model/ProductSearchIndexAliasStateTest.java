package com.project.young.productservice.application.searchindex.model;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class ProductSearchIndexAliasStateTest {
    @Test
    void classify_exactTarget_isConfirmed() {
        assertThat(state(Set.of("new"), true).classify("new", "old"))
                .isEqualTo(ProductSearchIndexAliasObservation.TARGET_CONFIRMED);
    }

    @Test
    void classify_previousOrAbsent_doesNotInferFailure() {
        assertThat(state(Set.of("old"), true).classify("new", "old"))
                .isEqualTo(ProductSearchIndexAliasObservation.PREVIOUS_OBSERVED);
        assertThat(state(Set.of(), true).classify("new", null))
                .isEqualTo(ProductSearchIndexAliasObservation.ALIAS_ABSENT);
    }

    @Test
    void classify_multipleOrUnexpectedIndices_requiresOperatorInspection() {
        assertThat(state(Set.of("new", "old"), true).classify("new", "old"))
                .isEqualTo(ProductSearchIndexAliasObservation.MULTIPLE_INDICES);
        assertThat(state(Set.of("other"), true).classify("new", "old"))
                .isEqualTo(ProductSearchIndexAliasObservation.UNEXPECTED_INDEX);
    }

    @Test
    void classify_filtersRoutingOrNameCollision_cannotConfirmEvenWithTarget() {
        assertThat(state(Set.of("new"), false).classify("new", "old"))
                .isEqualTo(ProductSearchIndexAliasObservation.ALIAS_OPTIONS_MISMATCH);
        assertThat(state(Set.of(), false).classify("new", null))
                .isEqualTo(ProductSearchIndexAliasObservation.ALIAS_OPTIONS_MISMATCH);
    }

    @Test
    void matches_nullMeansAbsent_andSnapshotIsImmutable() {
        var source = new HashSet<>(Set.of("new"));
        var snapshot = state(source, true);
        source.clear();
        assertThat(snapshot.matches("new")).isTrue();
        assertThat(snapshot.matches(null)).isFalse();
        assertThat(state(Set.of(), true).matches(null)).isTrue();
    }

    private static ProductSearchIndexAliasState state(Set<String> indices, boolean plain) {
        return new ProductSearchIndexAliasState(indices, plain);
    }
}

package com.project.young.productservice.application.searchindex.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSearchIndexVersionTest {

    private final Instant now = Instant.parse("2026-10-12T00:00:00Z");

    @Test
    void version_withSameMapping_createsDistinctPhysicalVersions() {
        var first = version(1);
        var second = version(2);
        assertThat(first.indexName()).isEqualTo("products-v1");
        assertThat(second.indexName()).isEqualTo("products-v2");
        assertThat(first.mappingHash()).isEqualTo(second.mappingHash());
    }

    @Test
    void lifecycle_afterBuildAndAliasConfirmation_canActivateAndReactivate() {
        var ready = version(1).beginBuild(now).markReady(now.plusSeconds(1));
        assertThat(ready.status()).isEqualTo(ProductSearchIndexStatus.READY);
        var activating = ready.beginActivation(now.plusSeconds(2));
        assertThat(activating.status()).isEqualTo(ProductSearchIndexStatus.ACTIVATING);
        assertThat(activating.activatedAt()).isNull();
        var active = activating.confirmActive(now.plusSeconds(3));
        assertThat(active.status()).isEqualTo(ProductSearchIndexStatus.ACTIVE);
        var inactive = active.deactivate(now.plusSeconds(4));
        assertThat(inactive.activatedAt()).isEqualTo(active.activatedAt());
        assertThat(inactive.beginActivation(now.plusSeconds(5)).confirmActive(now.plusSeconds(6)).status())
                .isEqualTo(ProductSearchIndexStatus.ACTIVE);
    }

    @Test
    void activation_beforeValidation_rejectsTransition() {
        assertThatThrownBy(() -> version(1).beginActivation(now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> version(1).beginBuild(now).beginActivation(now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> version(1).confirmActive(now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedBuild_cannotRestartSameVersion() {
        var failed = version(1).failBuild(now);
        assertThat(failed.status()).isEqualTo(ProductSearchIndexStatus.FAILED);
        assertThatThrownBy(() -> failed.beginBuild(now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ambiguousActivation_cannotBeMarkedAsBuildFailure() {
        var activating = version(1).beginBuild(now).markReady(now).beginActivation(now);
        assertThatThrownBy(() -> activating.failBuild(now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void transition_withEarlierTime_rejectsMutation() {
        assertThatThrownBy(() -> version(1).beginBuild(now.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activeIndex_withoutActivationTime_reportsMissingTimestamp() {
        assertThatThrownBy(() -> new ProductSearchIndexVersion(UUID.randomUUID(), 1, "products-v1", 1,
                "a".repeat(64), ProductSearchIndexStatus.ACTIVE, "admin-sub", now, now, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("activatedAt is required for ACTIVE indexes");
    }

    @Test
    void metadata_withMalformedMappingHash_reportsMappingError() {
        assertThatThrownBy(() -> ProductSearchIndexVersion.register(UUID.randomUUID(), 1, 1, "invalid", "admin-sub", now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("mappingHash must be a lowercase SHA-256 hex string");
    }

    @Test
    void metadata_withActivationAfterLastUpdate_reportsTimelineError() {
        assertThatThrownBy(() -> new ProductSearchIndexVersion(UUID.randomUUID(), 1, "products-v1", 1,
                "a".repeat(64), ProductSearchIndexStatus.ACTIVE, "admin-sub", now, now, now.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("activatedAt must be between createdAt and updatedAt");
    }

    private ProductSearchIndexVersion version(long number) {
        return ProductSearchIndexVersion.register(UUID.randomUUID(), number, 1, "a".repeat(64), "admin-sub", now);
    }
}

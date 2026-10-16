package com.project.young.productservice.application.searchindex.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSearchIndexBuildJobTest {

    private final Instant now = Instant.parse("2026-10-12T00:00:00Z");

    @Test
    void queuedJob_hasUnknownSourceAndZeroProgress() {
        var job = queued();
        assertThat(job.sourceProductCount()).isNull();
        assertThat(job.indexedProducts()).isZero();
        assertThat(job.startedAt()).isNull();
    }

    @Test
    void build_afterCountVerification_succeedsWithCompleteProgress() {
        var job = queued().start(3, now).recordCompletedBatch(2, now).recordCompletedBatch(1, now)
                .beginVerification(now).succeed(3, 3, now);
        assertThat(job.status()).isEqualTo(ProductSearchIndexBuildStatus.SUCCEEDED);
        assertThat(job.indexedProducts()).isEqualTo(3);
        assertThat(job.completedBatches()).isEqualTo(2);
        assertThat(job.finishedAt()).isEqualTo(now);
    }

    @Test
    void emptyCatalog_canSucceedWithoutBulkBatches() {
        var job = queued().start(0, now).beginVerification(now).succeed(0, 0, now);
        assertThat(job.status()).isEqualTo(ProductSearchIndexBuildStatus.SUCCEEDED);
    }

    @Test
    void verification_withChangedSourceOrMissingDocuments_rejectsSuccess() {
        var job = queued().start(2, now).recordCompletedBatch(2, now).beginVerification(now);
        assertThatThrownBy(() -> job.succeed(3, 2, now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> job.succeed(2, 1, now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void terminalFailure_retainsCompletedProgressAndCannotRestart() {
        var job = queued().start(3, now).recordCompletedBatch(2, now).fail("BULK_ITEM_FAILED", "Indexing failed", now);
        assertThat(job.indexedProducts()).isEqualTo(2);
        assertThat(job.failureCode()).isEqualTo("BULK_ITEM_FAILED");
        assertThatThrownBy(() -> job.start(3, now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> job.recordCompletedBatch(1, now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void claimedJob_onLostOwnership_canBeMarkedInterrupted() {
        var job = queued().start(3, now).interrupt("Execution ownership requires verification", now);
        assertThat(job.status()).isEqualTo(ProductSearchIndexBuildStatus.INTERRUPTED);
        assertThat(job.failureCode()).isEqualTo("WORKER_INTERRUPTED");
        assertThatThrownBy(() -> queued().interrupt("Interrupted", now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void progress_outsideConfiguredBatchOrSourceCount_isRejected() {
        var job = queued().start(1, now);
        assertThatThrownBy(() -> job.recordCompletedBatch(0, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> job.recordCompletedBatch(3, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> job.recordCompletedBatch(2, now)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transitions_inWrongPhaseOrBackwardsTime_areRejected() {
        assertThatThrownBy(() -> queued().beginVerification(now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> queued().start(1, now).succeed(1, 1, now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> queued().start(1, now.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ProductSearchIndexBuildStatus.class, names = {"RUNNING", "VERIFYING", "SUCCEEDED"})
    void startedJob_withMissingSourceCount_reportsValidationErrorInsteadOfUnboxingNull(ProductSearchIndexBuildStatus status) {
        assertThatThrownBy(() -> restored(status, null, 0, now,
                status == ProductSearchIndexBuildStatus.SUCCEEDED ? now : null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceProductCount is required");
    }

    @ParameterizedTest
    @EnumSource(value = ProductSearchIndexBuildStatus.class, names = {"SUCCEEDED", "FAILED", "INTERRUPTED"})
    void terminalJob_withoutFinishedAt_reportsMissingCompletionTime(ProductSearchIndexBuildStatus status) {
        assertThatThrownBy(() -> restored(status, 0L, 0, now, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finishedAt is required for terminal jobs");
    }

    @ParameterizedTest
    @EnumSource(value = ProductSearchIndexBuildStatus.class, names = {"QUEUED", "RUNNING", "VERIFYING"})
    void nonterminalJob_withFinishedAt_reportsUnexpectedCompletionTime(ProductSearchIndexBuildStatus status) {
        boolean queued = status == ProductSearchIndexBuildStatus.QUEUED;
        assertThatThrownBy(() -> restored(status, queued ? null : 0L, 0, queued ? null : now, now, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finishedAt must be absent for nonterminal jobs");
    }

    @ParameterizedTest
    @EnumSource(value = ProductSearchIndexBuildStatus.class, names = {"FAILED", "INTERRUPTED"})
    void failedJob_withoutFailureCode_reportsMissingFailureDetails(ProductSearchIndexBuildStatus status) {
        assertThatThrownBy(() -> restored(status, null, 0, null, now, null, "Failed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureCode is required");
    }

    @Test
    void queuedJob_withFailureDetails_rejectsInconsistentState() {
        assertThatThrownBy(() -> restored(ProductSearchIndexBuildStatus.QUEUED, null, 0, null, null,
                "INVALID_DOCUMENT", "Failed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failure details must be absent");
    }

    @Test
    void succeededJob_withIncompleteProgress_reportsCountMismatch() {
        assertThatThrownBy(() -> restored(ProductSearchIndexBuildStatus.SUCCEEDED, 1L, 0, now, now, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SUCCEEDED jobs must have indexed all source products");
    }

    @Test
    void failedJob_beforeStart_stillAllowsNullSourceAndStartTime() {
        var job = queued().fail("INVALID_DOCUMENT", "Failed", now);
        assertThat(job.sourceProductCount()).isNull();
        assertThat(job.startedAt()).isNull();
        assertThat(job.finishedAt()).isEqualTo(now);
    }

    private ProductSearchIndexBuildJob restored(ProductSearchIndexBuildStatus status, Long sourceCount,
                                                long indexed, Instant started, Instant finished,
                                                String failureCode, String failureMessage) {
        return new ProductSearchIndexBuildJob(UUID.randomUUID(), UUID.randomUUID(), "admin-sub", "request-1", 2,
                status, sourceCount, indexed, 0, now, now, started, finished, failureCode, failureMessage);
    }

    private ProductSearchIndexBuildJob queued() {
        return ProductSearchIndexBuildJob.queue(UUID.randomUUID(), UUID.randomUUID(), "admin-sub", "request-1", 2, now);
    }
}

package com.project.young.productservice.application.searchindex.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSearchIndexActivationJobTest {
    private final Instant now = Instant.parse("2026-10-14T00:00:00Z");
    private final UUID target = UUID.randomUUID();

    @Test
    void queued_withNoPreviousVersion_retainsExplicitInitialActivationIntent() {
        var job = queued();
        assertThat(job.expectedActiveVersionId()).isNull();
        assertThat(job.aliasName()).isEqualTo("products-read");
        assertThat(job.status()).isEqualTo(ProductSearchIndexActivationStatus.QUEUED);
        assertThat(job.startedAt()).isNull();
        assertThat(job.switchStartedAt()).isNull();
        assertThat(job.finishedAt()).isNull();
    }

    @Test
    void activation_afterExclusiveAliasConfirmation_succeeds() {
        var previous = UUID.randomUUID();
        var job = ProductSearchIndexActivationJob.queue(UUID.randomUUID(), target, previous,
                "products-read", "admin", "activate-1", now).start(now.plusSeconds(1))
                .beginSwitch(now.plusSeconds(2)).confirmActivation(target, now.plusSeconds(3));
        assertThat(job.status()).isEqualTo(ProductSearchIndexActivationStatus.SUCCEEDED);
        assertThat(job.expectedActiveVersionId()).isEqualTo(previous);
        assertThat(job.switchStartedAt()).isEqualTo(now.plusSeconds(2));
        assertThat(job.finishedAt()).isEqualTo(now.plusSeconds(3));
    }

    @Test
    void uncertainSwitch_remainsUnfinishedAndCanOnlySucceedAfterConfirmation() {
        var job = queued().start(now).beginSwitch(now).markUnconfirmed("ALIAS_RESULT_UNCONFIRMED", "결과 확인 필요", now);
        assertThat(job.status()).isEqualTo(ProductSearchIndexActivationStatus.UNCONFIRMED);
        assertThat(job.finishedAt()).isNull();
        assertThatThrownBy(() -> job.failBeforeSwitch("FAILED", "실패", now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> job.start(now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> job.beginSwitch(now)).isInstanceOf(IllegalStateException.class);
        var confirmed = job.confirmActivation(target, now.plusSeconds(1));
        assertThat(confirmed.status()).isEqualTo(ProductSearchIndexActivationStatus.SUCCEEDED);
        assertThat(confirmed.failureCode()).isNull();
        assertThat(confirmed.failureMessage()).isNull();
    }

    @Test
    void switch_withoutMatchingObservedVersion_cannotSucceedOrFailDirectly() {
        var job = queued().start(now).beginSwitch(now);
        assertThatThrownBy(() -> job.confirmActivation(null, now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> job.confirmActivation(UUID.randomUUID(), now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> job.failBeforeSwitch("FAILED", "실패", now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failure_beforeSwitch_canTerminateQueuedOrRunningWork() {
        assertThat(queued().failBeforeSwitch("PRECONDITION_FAILED", "조건 불일치", now).status())
                .isEqualTo(ProductSearchIndexActivationStatus.FAILED);
        var failed = queued().start(now).failBeforeSwitch("PRECONDITION_FAILED", "조건 불일치", now);
        assertThat(failed.switchStartedAt()).isNull();
        assertThat(failed.finishedAt()).isEqualTo(now);
        assertThatThrownBy(() -> failed.start(now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void transitions_inWrongPhaseOrAfterSuccess_areRejected() {
        assertThatThrownBy(() -> queued().beginSwitch(now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> queued().confirmActivation(target, now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> queued().start(now).markUnconfirmed("UNKNOWN", "확인 필요", now))
                .isInstanceOf(IllegalStateException.class);
        var succeeded = queued().start(now).beginSwitch(now).confirmActivation(target, now);
        assertThatThrownBy(() -> succeeded.markUnconfirmed("UNKNOWN", "확인 필요", now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> succeeded.failBeforeSwitch("FAILED", "실패", now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void transitions_withBackwardsTime_areRejected() {
        var running = queued().start(now.plusSeconds(2));
        assertThatThrownBy(() -> running.beginSwitch(now.plusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void intent_withTargetEqualToPreviousVersion_isRejected() {
        assertThatThrownBy(() -> ProductSearchIndexActivationJob.queue(UUID.randomUUID(), target, target,
                "products-read", "admin", "activate-1", now)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "*", "products-a,products-b", "Products-read", "_products-read"})
    void intent_withAmbiguousAlias_isRejected(String alias) {
        assertThatThrownBy(() -> ProductSearchIndexActivationJob.queue(UUID.randomUUID(), target, null,
                alias, "admin", "activate-1", now)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void intent_withInvalidRequesterOrIdempotencyKey_isRejected() {
        assertThatThrownBy(() -> ProductSearchIndexActivationJob.queue(UUID.randomUUID(), target, null,
                "products-read", " ", "activate-1", now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductSearchIndexActivationJob.queue(UUID.randomUUID(), target, null,
                "products-read", "admin", "invalid key", now)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ProductSearchIndexActivationStatus.class, names = {"SWITCHING", "UNCONFIRMED", "SUCCEEDED"})
    void restored_afterSwitch_withoutSwitchTimestamp_isRejected(ProductSearchIndexActivationStatus status) {
        assertThatThrownBy(() -> restored(status, now, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("switchStartedAt is required");
    }

    @Test
    void restored_failedAfterSwitch_orFinishedBeforeStart_isRejected() {
        assertThatThrownBy(() -> restored(ProductSearchIndexActivationStatus.FAILED, now, now, now, "FAILED", "실패"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must not have attempted");
        assertThatThrownBy(() -> restored(ProductSearchIndexActivationStatus.FAILED, now.plusSeconds(1), null, now, "FAILED", "실패"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("finishedAt must follow");
    }

    @Test
    void restored_uncertainWithCompletionTimestamp_orWithoutDiagnostic_isRejected() {
        assertThatThrownBy(() -> restored(ProductSearchIndexActivationStatus.UNCONFIRMED, now, now, now, "UNKNOWN", "확인 필요"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must not have finishedAt");
        assertThatThrownBy(() -> restored(ProductSearchIndexActivationStatus.UNCONFIRMED, now, now, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("failureCode");
        assertThatThrownBy(() -> queued().start(now).beginSwitch(now).markUnconfirmed("UNKNOWN", " ", now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("failureMessage");
    }

    private ProductSearchIndexActivationJob restored(ProductSearchIndexActivationStatus status, Instant start,
                                                     Instant switchStart, Instant finish, String code, String message) {
        return new ProductSearchIndexActivationJob(UUID.randomUUID(), target, null, "products-read", "admin", "activate-1",
                status, now, now.plusSeconds(2), start, switchStart, finish, code, message);
    }

    private ProductSearchIndexActivationJob queued() {
        return ProductSearchIndexActivationJob.queue(UUID.randomUUID(), target, null, "products-read", "admin", "activate-1", now);
    }
}

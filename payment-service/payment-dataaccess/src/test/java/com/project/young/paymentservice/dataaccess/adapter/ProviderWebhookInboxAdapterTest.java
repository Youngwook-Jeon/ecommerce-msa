package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.dto.command.ApplyProviderPaymentResultCommand;
import com.project.young.paymentservice.application.provider.ProviderWebhookInboxStatus;
import com.project.young.paymentservice.application.provider.ProviderPaymentResultOutcome;
import com.project.young.paymentservice.dataaccess.entity.ProviderWebhookInboxEntity;
import com.project.young.paymentservice.dataaccess.repository.ProviderWebhookInboxJpaRepository;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderWebhookInboxAdapterTest {

    @Test
    void recordReceived_insertsOnlyTheVerifiedCommand() {
        ProviderWebhookInboxJpaRepository repository = mock(ProviderWebhookInboxJpaRepository.class);
        ProviderWebhookInboxAdapter adapter = new ProviderWebhookInboxAdapter(repository);
        ApplyProviderPaymentResultCommand command = ApplyProviderPaymentResultCommand.succeeded(
                "evt_1", PaymentProvider.STRIPE, "pi_1");

        adapter.recordReceived(command);

        verify(repository).insertIfAbsent(
                eq("evt_1"), eq("STRIPE"), eq("pi_1"), eq(true), eq(ProviderPaymentResultOutcome.SUCCEEDED.name()), eq(null),
                eq(ProviderWebhookInboxStatus.RECEIVED.name()), any());
    }

    @Test
    void findReady_mapsReceivedAndWaitingEntriesToCommands() {
        ProviderWebhookInboxJpaRepository repository = mock(ProviderWebhookInboxJpaRepository.class);
        ProviderWebhookInboxAdapter adapter = new ProviderWebhookInboxAdapter(repository);
        when(repository.findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByReceivedAt(eq(List.of(
                ProviderWebhookInboxStatus.RECEIVED, ProviderWebhookInboxStatus.WAITING_FOR_PAYMENT)), any()))
                .thenReturn(List.of(ProviderWebhookInboxEntity.builder()
                        .eventId("evt_1")
                        .provider("STRIPE")
                        .providerPaymentId("pi_1")
                        .success(false)
                        .outcome(ProviderPaymentResultOutcome.ATTEMPT_FAILED)
                        .failureReason("declined")
                .status(ProviderWebhookInboxStatus.RECEIVED)
                        .nextRetryAt(Instant.now())
                        .receivedAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()));

        List<ApplyProviderPaymentResultCommand> commands = adapter.findReady(100);

        assertThat(commands).containsExactly(ApplyProviderPaymentResultCommand.paymentAttemptFailed(
                "evt_1", PaymentProvider.STRIPE, "pi_1", "declined"));
    }

    @Test
    void findEscalated_returnsSafeOperationalProjection() {
        ProviderWebhookInboxJpaRepository repository = mock(ProviderWebhookInboxJpaRepository.class);
        ProviderWebhookInboxAdapter adapter = new ProviderWebhookInboxAdapter(repository);
        when(repository.findTop100ByStatusOrderByUpdatedAt(ProviderWebhookInboxStatus.ESCALATED))
                .thenReturn(List.of(ProviderWebhookInboxEntity.builder()
                        .eventId("evt_1")
                        .provider("STRIPE")
                        .providerPaymentId("pi_1")
                        .success(true)
                        .outcome(ProviderPaymentResultOutcome.SUCCEEDED)
                        .status(ProviderWebhookInboxStatus.ESCALATED)
                        .attempts(20)
                        .lastFailureMessage("payment not associated")
                        .receivedAt(Instant.now())
                        .updatedAt(Instant.now())
                        .nextRetryAt(Instant.now())
                        .build()));

        var result = adapter.findEscalated(1);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().eventId()).isEqualTo("evt_1");
        assertThat(result.getFirst().failureMessage()).isEqualTo("payment not associated");
    }
}

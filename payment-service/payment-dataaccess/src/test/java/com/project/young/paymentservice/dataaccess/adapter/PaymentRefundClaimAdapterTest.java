package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.port.output.PaymentRefundClaimPort.Kind;
import com.project.young.paymentservice.application.port.output.PaymentRefundClaimPort.RefundAttempt;
import com.project.young.paymentservice.dataaccess.entity.PaymentRefundClaimEntity;
import com.project.young.paymentservice.dataaccess.repository.PaymentRefundClaimJpaRepository;
import com.project.young.paymentservice.domain.exception.PaymentRefundClaimConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRefundClaimAdapterTest {

    @Mock
    private PaymentRefundClaimJpaRepository repository;

    @Test
    void claimOrVerify_acceptsNewClaimAndSameOwnerRetry() {
        UUID paymentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        PaymentRefundClaimAdapter adapter = new PaymentRefundClaimAdapter(repository);
        PaymentRefundClaimEntity claim = claim(requestId, Kind.CUSTOMER);
        when(repository.insertIfAbsent(paymentId, requestId, Kind.CUSTOMER.name())).thenReturn(1, 0);
        when(repository.findById(paymentId)).thenReturn(Optional.of(claim));

        assertThatCode(() -> adapter.claimOrVerify(paymentId, requestId, Kind.CUSTOMER)).doesNotThrowAnyException();
        assertThatCode(() -> adapter.claimOrVerify(paymentId, requestId, Kind.CUSTOMER)).doesNotThrowAnyException();

        verify(repository, times(2)).insertIfAbsent(paymentId, requestId, Kind.CUSTOMER.name());
    }

    @Test
    void claimOrVerify_rejectsDifferentRefundOwner() {
        UUID paymentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        PaymentRefundClaimEntity claim = claim(requestId, Kind.COMPENSATION);
        when(repository.findById(paymentId)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> new PaymentRefundClaimAdapter(repository)
                .claimOrVerify(paymentId, requestId, Kind.CUSTOMER))
                .isInstanceOf(PaymentRefundClaimConflictException.class);
    }

    @Test
    void markAttemptStarted_recordsFirstAttemptWithoutHoldingALock() {
        UUID paymentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-27T00:00:00Z");
        PaymentRefundClaimEntity claim = claim(requestId, Kind.COMPENSATION);
        when(claim.getFirstAttemptAt()).thenReturn(now);
        when(repository.markAttemptStarted(paymentId, now)).thenReturn(1, 0);
        when(repository.findById(paymentId)).thenReturn(Optional.of(claim));
        PaymentRefundClaimAdapter adapter = new PaymentRefundClaimAdapter(repository);

        assertThat(adapter.markAttemptStarted(paymentId, requestId, Kind.COMPENSATION, now))
                .isEqualTo(new RefundAttempt(true, now));
        assertThat(adapter.markAttemptStarted(paymentId, requestId, Kind.COMPENSATION, now))
                .isEqualTo(new RefundAttempt(false, now));

        verify(repository, times(2)).markAttemptStarted(paymentId, now);
    }

    @Test
    void markAttemptStarted_whenClaimIsMissing_reportsInvariantFailureInsteadOfNpe() {
        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-27T00:00:00Z");
        when(repository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new PaymentRefundClaimAdapter(repository)
                .markAttemptStarted(paymentId, UUID.randomUUID(), Kind.CUSTOMER, now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refund claim is missing");
    }

    @Test
    void markAttemptStarted_whenAttemptTimeIsMissing_reportsInvariantFailureInsteadOfNpe() {
        UUID paymentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-27T00:00:00Z");
        PaymentRefundClaimEntity claim = claim(requestId, Kind.CUSTOMER);
        when(repository.findById(paymentId)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> new PaymentRefundClaimAdapter(repository)
                .markAttemptStarted(paymentId, requestId, Kind.CUSTOMER, now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing first refund attempt time");
    }

    private static PaymentRefundClaimEntity claim(UUID requestId, Kind kind) {
        PaymentRefundClaimEntity claim = mock(PaymentRefundClaimEntity.class);
        when(claim.getRequestId()).thenReturn(requestId);
        when(claim.getRequestKind()).thenReturn(kind.name());
        return claim;
    }
}

package com.project.young.paymentservice.dataaccess.adapter;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.project.young.paymentservice.application.port.output.CustomerRefundObservationPort.Observation;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundResult;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundState;
import com.project.young.paymentservice.application.port.output.PaymentRefundClaimPort.Kind;
import com.project.young.paymentservice.dataaccess.entity.PaymentRefundClaimEntity;
import com.project.young.paymentservice.dataaccess.repository.PaymentRefundClaimJpaRepository;
import com.project.young.paymentservice.domain.exception.PaymentRefundClaimConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerRefundObservationAdapterTest {

    @Mock
    private PaymentRefundClaimJpaRepository repository;

    private final UUID paymentId = UUID.randomUUID();
    private final UUID refundId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-29T00:00:00Z");
    private final RefundResult result = new RefundResult("re_customer", RefundState.SUCCEEDED);

    @Test
    void customerClaim_returnsRecordedObservation() {
        PaymentRefundClaimEntity claim = mock(PaymentRefundClaimEntity.class);
        when(repository.findById(paymentId)).thenReturn(Optional.of(claim));
        when(repository.recordCustomerObservation(paymentId, refundId, result.providerRefundId(),
                result.state().name(), now)).thenReturn(1);
        when(claim.getRequestId()).thenReturn(refundId);
        when(claim.getRequestKind()).thenReturn(Kind.CUSTOMER);
        when(claim.getProviderRefundId()).thenReturn(result.providerRefundId());
        when(claim.getProviderRefundSucceededAt()).thenReturn(now);

        assertThat(new CustomerRefundObservationAdapter(repository).record(paymentId, refundId, result, now))
                .contains(new Observation(now, null));
    }

    @Test
    void compensationClaim_isRejectedEvenWhenRequestIdMatches() {
        PaymentRefundClaimEntity claim = mock(PaymentRefundClaimEntity.class);
        when(repository.findById(paymentId)).thenReturn(Optional.of(claim));
        when(claim.getRequestId()).thenReturn(refundId);
        when(claim.getRequestKind()).thenReturn(Kind.COMPENSATION);

        assertThatThrownBy(() -> new CustomerRefundObservationAdapter(repository)
                .record(paymentId, refundId, result, now))
                .isInstanceOf(PaymentRefundClaimConflictException.class);
    }
}

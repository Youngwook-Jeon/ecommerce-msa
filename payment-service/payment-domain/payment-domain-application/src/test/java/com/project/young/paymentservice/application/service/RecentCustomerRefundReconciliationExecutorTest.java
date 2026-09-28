package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.port.output.PaymentRefundClaimPort;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.repository.PaymentRepository;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecentCustomerRefundReconciliationExecutorTest {
    @Test
    void recentSuccess_sweepsBoundedWindowThroughGetOnlyObserver() {
        var claims = mock(PaymentRefundClaimPort.class);
        var payments = mock(PaymentRepository.class);
        var observations = mock(ProviderRefundObservationApplicationService.class);
        Instant now = Instant.parse("2026-09-28T00:00:00Z");
        var claim = new PaymentRefundClaimPort.PendingRefund(UUID.randomUUID(), UUID.randomUUID(),
                PaymentRefundClaimPort.Kind.CUSTOMER, "re_1");
        when(claims.findRecentCustomerSuccesses(now.minus(Duration.ofDays(7)),
                now.minus(Duration.ofHours(6)), 100)).thenReturn(List.of(claim));
        var payment = mock(Payment.class);
        when(payments.findById(new PaymentId(claim.paymentId()))).thenReturn(Optional.of(payment));
        when(payment.getProvider()).thenReturn(PaymentProvider.STRIPE);
        when(payment.getProviderPaymentId()).thenReturn("pi_1");
        new RecentCustomerRefundReconciliationExecutor(claims, payments, observations,
                Clock.fixed(now, ZoneOffset.UTC), 7, Duration.ofHours(6).toMillis(), 100).reconcile();
        verify(observations).observe(argThat(c -> "re_1".equals(c.providerRefundId()) && "pi_1".equals(c.providerPaymentId())));
    }
}

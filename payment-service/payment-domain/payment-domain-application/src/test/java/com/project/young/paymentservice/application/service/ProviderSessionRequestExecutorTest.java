package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.port.output.ProviderSessionRequestPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderSessionRequestExecutorTest {

    @Test
    void executePending_completesSuccessfulRequest() {
        ProviderSessionRequestPort requests = mock(ProviderSessionRequestPort.class);
        PaymentApplicationService payments = mock(PaymentApplicationService.class);
        UUID paymentId = UUID.randomUUID();
        when(requests.claimPending(100)).thenReturn(List.of(paymentId));

        executor(requests, payments).executePending();

        verify(payments).createProviderSession(paymentId);
        verify(requests).complete(paymentId);
    }

    @Test
    void executePending_releasesFailedRequestForRetry() {
        ProviderSessionRequestPort requests = mock(ProviderSessionRequestPort.class);
        PaymentApplicationService payments = mock(PaymentApplicationService.class);
        UUID paymentId = UUID.randomUUID();
        when(requests.claimPending(100)).thenReturn(List.of(paymentId));
        doThrow(new IllegalStateException("stripe unavailable"))
                .when(payments).createProviderSession(paymentId);

        executor(requests, payments).executePending();

        verify(requests).release(paymentId, "stripe unavailable");
    }

    private static ProviderSessionRequestExecutor executor(ProviderSessionRequestPort requests, PaymentApplicationService payments) {
        return new ProviderSessionRequestExecutor(requests, payments,
                Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC), 300_000L, 5);
    }
}

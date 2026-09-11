package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.query.ProviderSessionRequestEscalationView;
import com.project.young.paymentservice.application.dto.query.ProviderWebhookInboxEscalationView;
import com.project.young.paymentservice.application.port.output.ProviderSessionRequestPort;
import com.project.young.paymentservice.application.port.output.ProviderWebhookInboxPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentOperationsQueryServiceTest {

    @Test
    void getEscalated_returnsBothOperationalQueuesUsingBoundedLimit() {
        ProviderSessionRequestPort sessionRequests = mock(ProviderSessionRequestPort.class);
        ProviderWebhookInboxPort webhookInbox = mock(ProviderWebhookInboxPort.class);
        PaymentOperationsQueryService service = new PaymentOperationsQueryService(sessionRequests, webhookInbox);
        ProviderSessionRequestEscalationView session = new ProviderSessionRequestEscalationView(
                UUID.randomUUID(), 5, "provider unavailable", Instant.now(), Instant.now());
        ProviderWebhookInboxEscalationView webhook = new ProviderWebhookInboxEscalationView(
                "evt_1", "STRIPE", "pi_1", "SUCCEEDED", 20, "payment not associated", Instant.now(), Instant.now());
        when(sessionRequests.findEscalated(500)).thenReturn(List.of(session));
        when(webhookInbox.findEscalated(500)).thenReturn(List.of(webhook));

        var result = service.getEscalated(999);

        assertThat(result.providerSessionRequests()).containsExactly(session);
        assertThat(result.providerWebhookInboxItems()).containsExactly(webhook);
        verify(sessionRequests).findEscalated(500);
        verify(webhookInbox).findEscalated(500);
    }
}

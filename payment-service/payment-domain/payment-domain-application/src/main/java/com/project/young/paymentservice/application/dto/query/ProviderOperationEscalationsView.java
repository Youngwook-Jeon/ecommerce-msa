package com.project.young.paymentservice.application.dto.query;

import java.util.List;

public record ProviderOperationEscalationsView(
        List<ProviderSessionRequestEscalationView> providerSessionRequests,
        List<ProviderWebhookInboxEscalationView> providerWebhookInboxItems
) {
}

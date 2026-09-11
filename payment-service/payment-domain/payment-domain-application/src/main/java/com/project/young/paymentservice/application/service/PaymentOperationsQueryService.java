package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.query.ProviderOperationEscalationsView;
import com.project.young.paymentservice.application.port.output.ProviderSessionRequestPort;
import com.project.young.paymentservice.application.port.output.ProviderWebhookInboxPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only operational view of provider work that exhausted automatic retries. */
@Service
public class PaymentOperationsQueryService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final ProviderSessionRequestPort providerSessionRequests;
    private final ProviderWebhookInboxPort providerWebhookInbox;

    public PaymentOperationsQueryService(
            ProviderSessionRequestPort providerSessionRequests,
            ProviderWebhookInboxPort providerWebhookInbox
    ) {
        this.providerSessionRequests = providerSessionRequests;
        this.providerWebhookInbox = providerWebhookInbox;
    }

    @Transactional(readOnly = true)
    public ProviderOperationEscalationsView getEscalated(int requestedLimit) {
        int limit = requestedLimit <= 0 ? DEFAULT_LIMIT : Math.min(requestedLimit, MAX_LIMIT);
        return new ProviderOperationEscalationsView(
                providerSessionRequests.findEscalated(limit),
                providerWebhookInbox.findEscalated(limit)
        );
    }
}

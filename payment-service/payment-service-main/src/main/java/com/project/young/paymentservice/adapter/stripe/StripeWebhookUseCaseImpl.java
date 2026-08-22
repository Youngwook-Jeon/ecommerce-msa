package com.project.young.paymentservice.adapter.stripe;

import com.project.young.paymentservice.application.port.input.StripeWebhookUseCase;
import com.project.young.paymentservice.application.port.output.StripeWebhookPort;
import com.project.young.paymentservice.application.service.PaymentApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@ConditionalOnProperty(prefix = "payment-service", name = "provider", havingValue = "stripe")
public class StripeWebhookUseCaseImpl implements StripeWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookUseCaseImpl.class);

    private final StripeWebhookPort stripeWebhookPort;
    private final PaymentApplicationService paymentApplicationService;

    public StripeWebhookUseCaseImpl(
            StripeWebhookPort stripeWebhookPort,
            PaymentApplicationService paymentApplicationService
    ) {
        this.stripeWebhookPort = stripeWebhookPort;
        this.paymentApplicationService = paymentApplicationService;
    }

    @Override
    @Transactional
    public void handle(String payload, String signatureHeader) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(signatureHeader, "signatureHeader must not be null");

        var command = stripeWebhookPort.verifyAndParse(payload, signatureHeader);
        if (command.isEmpty()) {
            log.debug("Ignoring Stripe webhook (unsupported or empty parse result)");
            return;
        }

        boolean applied = paymentApplicationService.applyProviderPaymentResult(command.get());
        log.info(
                "Stripe webhook eventId={} providerPaymentId={} applied={}",
                command.get().eventId(),
                command.get().providerPaymentId(),
                applied
        );
    }
}

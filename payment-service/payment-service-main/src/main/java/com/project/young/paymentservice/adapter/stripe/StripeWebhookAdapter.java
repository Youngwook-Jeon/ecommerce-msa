package com.project.young.paymentservice.adapter.stripe;

import com.project.young.paymentservice.application.dto.command.ApplyProviderPaymentResultCommand;
import com.project.young.paymentservice.application.exception.InvalidStripeWebhookException;
import com.project.young.paymentservice.application.port.output.StripeWebhookPort;
import com.project.young.paymentservice.config.StripeProperties;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "payment-service", name = "provider", havingValue = "stripe")
public class StripeWebhookAdapter implements StripeWebhookPort {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookAdapter.class);

    static final String EVENT_PAYMENT_INTENT_SUCCEEDED = "payment_intent.succeeded";
    static final String EVENT_PAYMENT_INTENT_PAYMENT_FAILED = "payment_intent.payment_failed";
    static final String EVENT_PAYMENT_INTENT_CANCELED = "payment_intent.canceled";

    private final StripeProperties stripeProperties;

    public StripeWebhookAdapter(StripeProperties stripeProperties) {
        this.stripeProperties = stripeProperties;
    }

    @Override
    public Optional<ApplyProviderPaymentResultCommand> verifyAndParse(String payload, String signatureHeader) {
        if (stripeProperties.webhookSecret() == null) {
            throw new InvalidStripeWebhookException(
                    "payment-service.stripe.webhook-secret must be set when provider=stripe");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, stripeProperties.webhookSecret());
        } catch (SignatureVerificationException ex) {
            throw new InvalidStripeWebhookException("Invalid Stripe webhook signature", ex);
        } catch (Exception ex) {
            throw new InvalidStripeWebhookException("Failed to parse Stripe webhook payload", ex);
        }

        return mapEvent(event);
    }

    Optional<ApplyProviderPaymentResultCommand> mapEvent(Event event) {
        String type = event.getType();
        if (type == null) {
            return Optional.empty();
        }

        Optional<PaymentIntent> paymentIntent = extractPaymentIntent(event);
        if (paymentIntent.isEmpty()) {
            log.debug("Stripe event {} has no PaymentIntent payload; ignoring", type);
            return Optional.empty();
        }

        String providerPaymentId = paymentIntent.get().getId();
        return switch (type) {
            case EVENT_PAYMENT_INTENT_SUCCEEDED -> Optional.of(
                    ApplyProviderPaymentResultCommand.succeeded(
                            event.getId(),
                            PaymentProvider.STRIPE,
                            providerPaymentId
                    )
            );
            case EVENT_PAYMENT_INTENT_PAYMENT_FAILED -> Optional.of(
                    ApplyProviderPaymentResultCommand.paymentAttemptFailed(
                            event.getId(),
                            PaymentProvider.STRIPE,
                            providerPaymentId,
                            failureReason(paymentIntent.get(), type)
                    )
            );
            case EVENT_PAYMENT_INTENT_CANCELED -> Optional.of(
                    ApplyProviderPaymentResultCommand.finalFailure(
                            event.getId(),
                            PaymentProvider.STRIPE,
                            providerPaymentId,
                            failureReason(paymentIntent.get(), type)
                    )
            );
            default -> {
                log.debug("Ignoring unsupported Stripe event type {}", type);
                yield Optional.empty();
            }
        };
    }

    private static Optional<PaymentIntent> extractPaymentIntent(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer()
                .getObject()
                .orElse(null);
        if (stripeObject instanceof PaymentIntent paymentIntent) {
            return Optional.of(paymentIntent);
        }
        log.debug("Stripe event {} data object is not a PaymentIntent (type={})",
                event.getId(),
                stripeObject == null ? "null" : stripeObject.getClass().getSimpleName());
        return Optional.empty();
    }

    private static String failureReason(PaymentIntent intent, String eventType) {
        if (intent.getLastPaymentError() != null
                && intent.getLastPaymentError().getMessage() != null
                && !intent.getLastPaymentError().getMessage().isBlank()) {
            return intent.getLastPaymentError().getMessage();
        }
        if (EVENT_PAYMENT_INTENT_CANCELED.equals(eventType)) {
            return "Stripe PaymentIntent was canceled";
        }
        return "Stripe payment failed";
    }
}

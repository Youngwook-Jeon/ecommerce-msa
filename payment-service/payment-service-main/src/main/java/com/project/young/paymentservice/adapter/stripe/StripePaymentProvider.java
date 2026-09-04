package com.project.young.paymentservice.adapter.stripe;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.exception.PaymentDomainException;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Creates Stripe PaymentIntents and returns an async session (client secret for Embedded Elements).
 * Settlement happens later via webhook.
 */
@Component
@ConditionalOnProperty(prefix = "payment-service", name = "provider", havingValue = "stripe")
public class StripePaymentProvider implements PaymentProviderPort {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentProvider.class);

    @Override
    public ProviderPaymentSession createPayment(Payment payment) {
        Objects.requireNonNull(payment, "payment must not be null");
        try {
            long amountMinor = toMinorUnits(payment.getAmount(), payment.getCurrency());
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountMinor)
                    .setCurrency(payment.getCurrency().toLowerCase())
                    .putMetadata("payment_id", payment.getId().getValue().toString())
                    .putMetadata("order_id", payment.getOrderId().getValue().toString())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            if (intent.getClientSecret() == null || intent.getClientSecret().isBlank()) {
                throw new PaymentDomainException("Stripe PaymentIntent returned empty client_secret");
            }

            log.info(
                    "Created Stripe PaymentIntent {} for payment {} order {}",
                    intent.getId(),
                    payment.getId().getValue(),
                    payment.getOrderId().getValue()
            );
            return ProviderPaymentSession.async(
                    PaymentProvider.STRIPE,
                    intent.getId(),
                    intent.getClientSecret()
            );
        } catch (PaymentDomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PaymentDomainException("Failed to create Stripe PaymentIntent: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void refund(Payment payment, String idempotencyKey) {
        try {
            log.info("Creating Stripe refund for payment {} with idempotency key {}", payment.getId().getValue(), idempotencyKey);
            Refund.create(
                    RefundCreateParams.builder().setPaymentIntent(payment.getProviderPaymentId()).build(),
                    RequestOptions.builder().setIdempotencyKey(idempotencyKey).build()
            );
            log.info("Stripe refund completed for payment {}", payment.getId().getValue());
        } catch (Exception ex) {
            log.warn("Stripe refund failed for payment {}", payment.getId().getValue(), ex);
            throw new PaymentDomainException("Failed to refund Stripe payment: " + ex.getMessage(), ex);
        }
    }

    static long toMinorUnits(Money money, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits < 0) {
            throw new PaymentDomainException("Unsupported currency for Stripe minor units: " + currencyCode);
        }
        return money.getAmount()
                .movePointRight(fractionDigits)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
    }
}

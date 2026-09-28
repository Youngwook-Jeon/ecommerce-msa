package com.project.young.paymentservice.adapter.stripe;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.exception.PaymentDomainException;
import com.project.young.paymentservice.domain.exception.PaymentRefundRejectedException;
import com.project.young.paymentservice.domain.exception.PaymentRefundUnavailableException;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.RefundListParams;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import java.util.Optional;

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

            String idempotencyKey = "payment-intent:" + payment.getId().getValue();
            PaymentIntent intent = PaymentIntent.create(
                    params,
                    RequestOptions.builder().setIdempotencyKey(idempotencyKey).build()
            );
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
    public RefundResult refund(Payment payment, String idempotencyKey) {
        try {
            log.info("Creating Stripe refund for payment {} with idempotency key {}", payment.getId().getValue(), idempotencyKey);
            Refund refund = Refund.create(
                    RefundCreateParams.builder().setPaymentIntent(payment.getProviderPaymentId()).build(),
                    RequestOptions.builder().setIdempotencyKey(idempotencyKey).build()
            );
            log.info("Stripe refund accepted for payment {} refundId={} status={}",
                    payment.getId().getValue(), refund.getId(), refund.getStatus());
            return toRefundResult(refund);
        } catch (PaymentRefundRejectedException ex) {
            throw ex;
        } catch (StripeException ex) {
            Integer statusCode = ex.getStatusCode();
            if (statusCode != null && statusCode >= 400 && statusCode < 500
                    && statusCode != 408 && statusCode != 409 && statusCode != 425 && statusCode != 429) {
                log.warn("Stripe rejected refund for payment {} status={}", payment.getId().getValue(), statusCode, ex);
                throw new PaymentRefundRejectedException("Stripe rejected the refund for payment "
                        + payment.getId().getValue(), ex);
            }
            log.warn("Stripe refund unavailable for payment {} status={}", payment.getId().getValue(), statusCode, ex);
            throw new PaymentRefundUnavailableException("Stripe refund result is uncertain for payment "
                    + payment.getId().getValue(), ex);
        } catch (Exception ex) {
            log.warn("Stripe refund failed for payment {}", payment.getId().getValue(), ex);
            throw new PaymentRefundUnavailableException("Stripe refund result is uncertain for payment "
                    + payment.getId().getValue(), ex);
        }
    }

    @Override
    public Optional<RefundResult> findFullRefund(Payment payment) {
        try {
            RefundListParams params = RefundListParams.builder()
                    .setPaymentIntent(payment.getProviderPaymentId())
                    .setLimit(100L)
                    .build();
            return findFullRefund(Refund.list(params).autoPagingIterable(), payment);
        } catch (PaymentRefundRejectedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PaymentRefundUnavailableException("Could not inspect Stripe refunds for payment "
                    + payment.getId().getValue(), ex);
        }
    }

    static Optional<RefundResult> findFullRefund(Iterable<Refund> refunds, Payment payment) {
        long expectedAmount = toMinorUnits(payment.getAmount(), payment.getCurrency());
        RefundResult found = null;
        for (Refund refund : refunds) {
            if (!Long.valueOf(expectedAmount).equals(refund.getAmount())) {
                throw new PaymentRefundRejectedException("Unexpected partial refund exists for payment "
                        + payment.getId().getValue());
            }
            if ("succeeded".equals(refund.getStatus()) || "pending".equals(refund.getStatus())
                    || "failed".equals(refund.getStatus()) || "canceled".equals(refund.getStatus())) {
                if (found != null) {
                    throw new PaymentRefundRejectedException("Multiple refunds require manual review for payment "
                            + payment.getId().getValue());
                }
                log.info("Found existing Stripe refund paymentId={} providerRefundId={} status={}",
                        payment.getId().getValue(), refund.getId(), refund.getStatus());
                found = toRefundResult(refund);
                continue;
            }
            throw new PaymentRefundRejectedException("Stripe refund requires manual review for payment "
                    + payment.getId().getValue() + " (status=" + refund.getStatus() + ")");
        }
        return Optional.ofNullable(found);
    }

    @Override
    public RefundResult retrieveRefund(String providerRefundId) {
        try {
            return toRefundResult(Refund.retrieve(providerRefundId));
        } catch (StripeException ex) {
            throw new PaymentRefundUnavailableException("Could not retrieve Stripe refund " + providerRefundId, ex);
        }
    }

    private static RefundResult toRefundResult(Refund refund) {
        RefundState state = switch (refund.getStatus()) {
            case "pending", "requires_action" -> RefundState.PENDING;
            case "succeeded" -> RefundState.SUCCEEDED;
            case "failed", "canceled" -> RefundState.FAILED;
            default -> throw new PaymentRefundRejectedException("Unknown Stripe refund status: " + refund.getStatus());
        };
        return new RefundResult(refund.getId(), state);
    }

    @Override
    public Optional<ProviderPaymentResult> retrieveTerminalResult(Payment payment) {
        Objects.requireNonNull(payment, "payment must not be null");
        if (payment.getProvider() != PaymentProvider.STRIPE || payment.getProviderPaymentId() == null) {
            throw new PaymentDomainException("Stripe reconciliation requires a Stripe provider payment id");
        }
        try {
            PaymentIntent intent = PaymentIntent.retrieve(payment.getProviderPaymentId());
            Optional<ProviderPaymentResult> result = toTerminalResult(intent);
            log.info(
                    "Reconciled Stripe PaymentIntent paymentId={} providerPaymentId={} terminalOutcome={}",
                    payment.getId().getValue(),
                    payment.getProviderPaymentId(),
                    result.map(value -> value.outcome().name()).orElse("PENDING")
            );
            return result;
        } catch (PaymentDomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PaymentDomainException(
                    "Failed to retrieve Stripe PaymentIntent " + payment.getProviderPaymentId() + ": " + ex.getMessage(), ex);
        }
    }

    static Optional<ProviderPaymentResult> toTerminalResult(PaymentIntent intent) {
        if (intent == null || intent.getStatus() == null) {
            return Optional.empty();
        }
        return switch (intent.getStatus()) {
            case "succeeded" -> Optional.of(ProviderPaymentResult.succeeded());
            case "canceled" -> Optional.of(ProviderPaymentResult.finalFailure("Stripe PaymentIntent was canceled"));
            default -> Optional.empty();
        };
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

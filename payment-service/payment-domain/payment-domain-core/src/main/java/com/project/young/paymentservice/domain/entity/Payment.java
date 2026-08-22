package com.project.young.paymentservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.domain.exception.PaymentDomainException;
import com.project.young.paymentservice.domain.exception.PaymentStateConflictException;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import com.project.young.paymentservice.domain.valueobject.PaymentStatus;
import com.project.young.paymentservice.domain.valueobject.UserId;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

public class Payment extends AggregateRoot<PaymentId> {

    public static final String DEFAULT_CURRENCY = "USD";

    /** Matches {@code payments.failure_reason VARCHAR(500)}. */
    private static final int FAILURE_REASON_MAX_LENGTH = 500;

    private final OrderId orderId;
    private final UserId userId;
    private final Money amount;
    private final String currency;
    private PaymentStatus status;
    private String failureReason;
    private PaymentProvider provider;
    private String providerPaymentId;
    private String clientSecret;
    private Instant createdAt;
    private Instant updatedAt;

    private Payment(Builder builder) {
        super.setId(builder.paymentId);
        this.orderId = builder.orderId;
        this.userId = builder.userId;
        this.amount = builder.amount;
        this.currency = builder.currency;
        this.status = builder.status;
        this.failureReason = builder.failureReason;
        this.provider = builder.provider;
        this.providerPaymentId = builder.providerPaymentId;
        this.clientSecret = builder.clientSecret;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    private Payment(
            PaymentId paymentId,
            OrderId orderId,
            UserId userId,
            Money amount,
            String currency,
            PaymentStatus status,
            String failureReason,
            PaymentProvider provider,
            String providerPaymentId,
            String clientSecret,
            Instant createdAt,
            Instant updatedAt
    ) {
        super.setId(paymentId);
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.failureReason = failureReason;
        this.provider = provider;
        this.providerPaymentId = providerPaymentId;
        this.clientSecret = clientSecret;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Payment createPending(
            PaymentId paymentId,
            OrderId orderId,
            UserId userId,
            Money amount
    ) {
        return createPending(paymentId, orderId, userId, amount, DEFAULT_CURRENCY);
    }

    public static Payment createPending(
            PaymentId paymentId,
            OrderId orderId,
            UserId userId,
            Money amount,
            String currency
    ) {
        return builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.PENDING)
                .build();
    }

    /**
     * !!! FOR PERSISTENCE MAPPING ONLY !!!
     * Reconstitutes a Payment from persistent state (e.g. database).
     * Bypasses creation validations and must NOT be used to create new business objects.
     * Use {@link #createPending} or {@link #builder()} for new instances.
     */
    public static Payment reconstitute(
            PaymentId paymentId,
            OrderId orderId,
            UserId userId,
            Money amount,
            String currency,
            PaymentStatus status,
            String failureReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        return reconstitute(
                paymentId,
                orderId,
                userId,
                amount,
                currency,
                status,
                failureReason,
                null,
                null,
                null,
                createdAt,
                updatedAt
        );
    }

    public static Payment reconstitute(
            PaymentId paymentId,
            OrderId orderId,
            UserId userId,
            Money amount,
            String currency,
            PaymentStatus status,
            String failureReason,
            PaymentProvider provider,
            String providerPaymentId,
            String clientSecret,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Payment(
                paymentId,
                orderId,
                userId,
                amount,
                currency,
                status,
                failureReason,
                provider,
                providerPaymentId,
                clientSecret,
                createdAt,
                updatedAt
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Attaches an external PSP session (PaymentIntent id + client secret) while still PENDING.
     */
    public void assignProviderSession(PaymentProvider provider, String providerPaymentId, String clientSecret) {
        Objects.requireNonNull(provider, "provider must not be null");
        String normalizedProviderPaymentId = normalizeRequiredText(providerPaymentId, "providerPaymentId");
        String normalizedClientSecret = normalizeRequiredText(clientSecret, "clientSecret");

        if (status != PaymentStatus.PENDING) {
            throw new PaymentStateConflictException(
                    "Cannot assign provider session unless payment is PENDING (was " + status + ").");
        }
        if (this.providerPaymentId != null
                && (!Objects.equals(this.provider, provider)
                || !this.providerPaymentId.equals(normalizedProviderPaymentId))) {
            throw new PaymentStateConflictException(
                    "Payment already has a different provider session (" + this.provider + "/"
                            + this.providerPaymentId + ").");
        }

        this.provider = provider;
        this.providerPaymentId = normalizedProviderPaymentId;
        this.clientSecret = normalizedClientSecret;
    }

    public boolean hasProviderSession() {
        return provider != null && providerPaymentId != null && clientSecret != null;
    }

    public void complete() {
        transitionTo(PaymentStatus.COMPLETED);
        failureReason = null;
    }

    public void fail(String reason) {
        String normalizedReason = normalizeFailureReason(reason);
        if (status == PaymentStatus.FAILED) {
            return;
        }
        transitionTo(PaymentStatus.FAILED);
        failureReason = normalizedReason;
    }

    private void transitionTo(PaymentStatus target) {
        Objects.requireNonNull(target, "target status must not be null");
        if (status == target) {
            return;
        }
        if (!status.canTransitionTo(target)) {
            throw new PaymentStateConflictException(
                    "Cannot transition payment from " + status + " to " + target + ".");
        }
        status = target;
    }

    private static void validateAmount(Money amount) {
        if (amount == null) {
            throw new PaymentDomainException("Payment amount cannot be null.");
        }
        if (!amount.isGreaterThanZero()) {
            throw new PaymentDomainException("Payment amount must be greater than zero.");
        }
        if (amount.exceedsMax()) {
            throw new PaymentDomainException(
                    "Payment amount must not exceed " + Money.MAX.getAmount().toPlainString() + ".");
        }
    }

    private static String normalizeCurrency(String currency) {
        Objects.requireNonNull(currency, "currency must not be null");
        String normalized = currency.trim().toUpperCase();
        if (normalized.length() != 3) {
            throw new PaymentDomainException(
                    "currency must be a 3-letter ISO 4217 code, got: '" + currency + "'.");
        }
        try {
            Currency.getInstance(normalized);
        } catch (IllegalArgumentException ex) {
            throw new PaymentDomainException("Unknown currency code: '" + currency + "'.");
        }
        return normalized;
    }

    private static String normalizeFailureReason(String reason) {
        Objects.requireNonNull(reason, "failure reason must not be null");
        String normalized = reason.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("failure reason must not be blank");
        }
        if (normalized.length() > FAILURE_REASON_MAX_LENGTH) {
            throw new PaymentDomainException(
                    "failure reason must not exceed " + FAILURE_REASON_MAX_LENGTH + " characters.");
        }
        return normalized;
    }

    private static String normalizeRequiredText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new PaymentDomainException(fieldName + " must not be blank.");
        }
        return normalized;
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public UserId getUserId() {
        return userId;
    }

    public Money getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static final class Builder {
        private PaymentId paymentId;
        private OrderId orderId;
        private UserId userId;
        private Money amount;
        private String currency = DEFAULT_CURRENCY;
        private PaymentStatus status = PaymentStatus.PENDING;
        private String failureReason;
        private PaymentProvider provider;
        private String providerPaymentId;
        private String clientSecret;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder paymentId(PaymentId paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public Builder orderId(OrderId orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder userId(UserId userId) {
            this.userId = userId;
            return this;
        }

        public Builder amount(Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder status(PaymentStatus status) {
            this.status = status;
            return this;
        }

        public Builder failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }

        public Builder provider(PaymentProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder providerPaymentId(String providerPaymentId) {
            this.providerPaymentId = providerPaymentId;
            return this;
        }

        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Payment build() {
            validate();
            this.currency = normalizeCurrency(this.currency);
            return new Payment(this);
        }

        private void validate() {
            if (paymentId == null) {
                throw new PaymentDomainException("Payment id cannot be null.");
            }
            if (orderId == null) {
                throw new PaymentDomainException("Order id cannot be null.");
            }
            if (userId == null) {
                throw new PaymentDomainException("User id cannot be null.");
            }
            if (currency == null) {
                throw new PaymentDomainException("currency cannot be null.");
            }
            validateAmount(amount);
            if (status == null) {
                throw new PaymentDomainException("Payment status cannot be null.");
            }
            if (status != PaymentStatus.PENDING) {
                throw new PaymentDomainException("Payment must be created with PENDING status.");
            }
            if (failureReason != null) {
                throw new PaymentDomainException("Pending payment must not have a failure reason.");
            }
        }
    }
}

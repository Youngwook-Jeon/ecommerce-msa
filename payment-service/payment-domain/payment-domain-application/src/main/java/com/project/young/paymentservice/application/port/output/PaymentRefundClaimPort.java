package com.project.young.paymentservice.application.port.output;

import java.util.Optional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundState;

/** Ensures a payment is refunded by at most one business request and PSP key. */
public interface PaymentRefundClaimPort {

    enum Kind {
        COMPENSATION,
        CUSTOMER
    }

    /** Commits the owner before calling the PSP; the same request may retry. */
    void claimOrVerify(UUID paymentId, UUID requestId, Kind kind);

    /** Commits the first PSP-attempt time; later attempts must inspect the PSP before retrying. */
    RefundAttempt markAttemptStarted(UUID paymentId, UUID requestId, Kind kind, Instant now);

    void recordProviderResult(UUID paymentId, UUID requestId, Kind kind, String providerRefundId, RefundState state);

    List<PendingRefund> findUnfinalized(int limit);

    boolean isCustomerReviewEscalated(UUID paymentId, UUID requestId);

    Optional<PendingRefund> findByProviderRefundId(String providerRefundId);

    List<PendingRefund> findRecentCustomerSuccesses(Instant succeededSince, Instant checkedBefore, int limit);

    record PendingRefund(UUID paymentId, UUID requestId, Kind kind, String providerRefundId) {
    }

    record RefundAttempt(boolean firstAttempt, Instant startedAt, String providerRefundId) {
    }
}

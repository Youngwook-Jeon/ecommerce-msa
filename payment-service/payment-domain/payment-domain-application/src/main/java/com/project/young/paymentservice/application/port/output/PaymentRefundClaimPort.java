package com.project.young.paymentservice.application.port.output;

import java.time.Instant;
import java.util.UUID;

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

    record RefundAttempt(boolean firstAttempt, Instant startedAt) {
    }
}

package com.project.young.paymentservice.application.port.output;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Serializes only the short DB result transaction; never holds a lock across a PSP call. */
public interface CustomerRefundObservationPort {
    Optional<Observation> record(UUID paymentId, UUID refundId, PaymentProviderPort.RefundResult result, Instant now);

    record Observation(Instant succeededAt, Instant failedAt) {
    }
}

package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.port.output.CustomerRefundObservationPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort;
import com.project.young.paymentservice.application.port.output.PaymentRefundClaimPort.Kind;
import com.project.young.paymentservice.dataaccess.repository.PaymentRefundClaimJpaRepository;
import com.project.young.paymentservice.domain.exception.PaymentRefundClaimConflictException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CustomerRefundObservationAdapter implements CustomerRefundObservationPort {

    private final PaymentRefundClaimJpaRepository repository;

    public CustomerRefundObservationAdapter(PaymentRefundClaimJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Optional<Observation> record(UUID paymentId, UUID refundId, PaymentProviderPort.RefundResult result, Instant now) {
        int updated = repository.recordCustomerObservation(paymentId, refundId, result.providerRefundId(),
                result.state().name(), now);
        var claim = repository.findById(paymentId).orElseThrow(() ->
                new PaymentRefundClaimConflictException("Missing customer refund claim: " + paymentId));
        if (!refundId.equals(claim.getRequestId()) || claim.getRequestKind() != Kind.CUSTOMER
                || !result.providerRefundId().equals(claim.getProviderRefundId())) {
            throw new PaymentRefundClaimConflictException("Customer refund result conflicts with claim: " + paymentId);
        }
        return updated == 1 ? Optional.of(new Observation(claim.getProviderRefundSucceededAt(),
                claim.getProviderRefundFailedAt())) : Optional.empty();
    }
}

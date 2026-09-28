package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.ObserveProviderRefundCommand;
import com.project.young.paymentservice.application.dto.command.RefundCustomerPaymentCommand;
import com.project.young.paymentservice.application.dto.command.RecordRefundCompensationDltCommand;
import com.project.young.paymentservice.application.port.output.PaymentRefundClaimPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundResult;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundState;
import com.project.young.paymentservice.domain.repository.PaymentRepository;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Observes the existing PSP refund only: it never initiates or retries a monetary operation. */
@Service
public class ProviderRefundObservationApplicationService {
    private static final Logger log = LoggerFactory.getLogger(ProviderRefundObservationApplicationService.class);
    private final PaymentRefundClaimPort claims;
    private final PaymentRepository payments;
    private final PaymentProviderPort provider;
    private final PaymentRefundResultRecorder results;
    private final RefundCompensationDltApplicationService compensationOperations;
    private final String compensationTopic;
    private final String compensationDltTopic;

    public ProviderRefundObservationApplicationService(PaymentRefundClaimPort claims, PaymentRepository payments,
            PaymentProviderPort provider, PaymentRefundResultRecorder results,
            RefundCompensationDltApplicationService compensationOperations,
            @Value("${payment-service.saga-events.refund-requested-topic:payment.refund.requested}") String compensationTopic,
            @Value("${payment-service.saga-events.refund-requested-dlt-topic:payment.refund.requested.DLT}") String compensationDltTopic) {
        this.claims = claims;
        this.payments = payments;
        this.provider = provider;
        this.results = results;
        this.compensationOperations = compensationOperations;
        this.compensationTopic = compensationTopic;
        this.compensationDltTopic = compensationDltTopic;
    }

    public boolean observe(ObserveProviderRefundCommand command) {
        var claim = claims.findByProviderRefundId(command.providerRefundId());
        if (claim.isEmpty()) {
            log.debug("Refund webhook awaiting claim association eventId={} providerRefundId={}",
                    command.eventId(), command.providerRefundId());
            return false;
        }
        var owner = claim.get();
        var payment = payments.findById(new PaymentId(owner.paymentId())).orElseThrow(() ->
                new IllegalStateException("Refund payment missing: " + owner.paymentId()));
        if (payment.getProvider() != PaymentProvider.STRIPE
                || !command.providerPaymentId().equals(payment.getProviderPaymentId())) {
            throw new IllegalArgumentException("Refund webhook does not match provider payment");
        }
        // A signed failure is authoritative. Other notifications trigger GET so stale snapshots cannot revive failure.
        RefundResult result = command.state() == RefundState.FAILED
                ? new RefundResult(command.providerRefundId(), RefundState.FAILED)
                : provider.retrieveRefund(command.providerRefundId());
        if (!command.providerRefundId().equals(result.providerRefundId())) {
            throw new IllegalArgumentException("PSP returned a different refund");
        }
        if (owner.kind() == PaymentRefundClaimPort.Kind.CUSTOMER) {
            results.recordCustomerObservation(new RefundCustomerPaymentCommand(owner.requestId(), owner.paymentId(),
                    payment.getOrderId().getValue(), payment.getUserId().value()), result, command.failureReason());
        } else if (result.state() == RefundState.FAILED) {
            claims.recordProviderResult(owner.paymentId(), owner.requestId(), owner.kind(),
                    result.providerRefundId(), result.state());
            compensationOperations.recordManualFollowUp(new RecordRefundCompensationDltCommand(
                    owner.requestId(), owner.paymentId(), payment.getOrderId().getValue(), compensationTopic,
                    compensationDltTopic, null, null, "ProviderRefundFailed", "PSP refund failed after observation"));
        }
        log.info("Observed provider refund eventId={} requestId={} providerRefundId={} state={}",
                command.eventId(), owner.requestId(), result.providerRefundId(), result.state());
        return true;
    }
}

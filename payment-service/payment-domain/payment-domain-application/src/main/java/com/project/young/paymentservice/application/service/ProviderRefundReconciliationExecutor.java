package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.RefundCustomerPaymentCommand;
import com.project.young.paymentservice.application.dto.command.RefundPaymentCommand;
import com.project.young.paymentservice.application.dto.command.RecordRefundCompensationDltCommand;
import com.project.young.paymentservice.application.dto.command.EscalateCustomerRefundCommand;
import com.project.young.paymentservice.application.port.output.PaymentRefundClaimPort;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.exception.PaymentRefundRejectedException;
import com.project.young.paymentservice.domain.repository.PaymentRepository;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Rechecks accepted and uncertain PSP refunds without holding a database transaction over HTTP. */
@Component
public class ProviderRefundReconciliationExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProviderRefundReconciliationExecutor.class);

    private final PaymentRefundClaimPort claims;
    private final PaymentRepository payments;
    private final PaymentApplicationService applicationService;
    private final RefundCompensationDltApplicationService compensationOperations;
    private final CustomerRefundReviewApplicationService customerOperations;
    private final String refundRequestedTopic;
    private final String refundRequestedDltTopic;

    public ProviderRefundReconciliationExecutor(PaymentRefundClaimPort claims, PaymentRepository payments,
                                                PaymentApplicationService applicationService,
                                                RefundCompensationDltApplicationService compensationOperations,
                                                CustomerRefundReviewApplicationService customerOperations,
                                                @Value("${payment-service.saga-events.refund-requested-topic:payment.refund.requested}")
                                                String refundRequestedTopic,
                                                @Value("${payment-service.saga-events.refund-requested-dlt-topic:payment.refund.requested.DLT}")
                                                String refundRequestedDltTopic) {
        this.claims = claims;
        this.payments = payments;
        this.applicationService = applicationService;
        this.compensationOperations = compensationOperations;
        this.customerOperations = customerOperations;
        this.refundRequestedTopic = refundRequestedTopic;
        this.refundRequestedDltTopic = refundRequestedDltTopic;
    }

    @Scheduled(fixedDelayString = "${payment-service.provider-refund-reconciliation.fixed-delay-ms:60000}")
    public void reconcileRefunds() {
        for (PaymentRefundClaimPort.PendingRefund claim : claims.findUnfinalized(100)) {
            try {
                Payment payment = payments.findById(new PaymentId(claim.paymentId()))
                        .orElseThrow(() -> new IllegalStateException("Refund payment missing: " + claim.paymentId()));
                boolean completed = switch (claim.kind()) {
                    case CUSTOMER -> applicationService.refundCustomerPayment(new RefundCustomerPaymentCommand(
                            claim.requestId(), claim.paymentId(), payment.getOrderId().getValue(),
                            payment.getUserId().value()));
                    case COMPENSATION -> applicationService.refundPayment(new RefundPaymentCommand(
                            claim.requestId(), claim.paymentId(), payment.getOrderId().getValue()));
                };
                log.debug("Reconciled provider refund paymentId={} requestId={} providerRefundId={} completed={}",
                        claim.paymentId(), claim.requestId(), claim.providerRefundId(), completed);
            } catch (PaymentRefundRejectedException ex) {
                if (claim.kind() == PaymentRefundClaimPort.Kind.COMPENSATION) {
                    try {
                        Payment payment = payments.findById(new PaymentId(claim.paymentId()))
                                .orElseThrow(() -> new IllegalStateException("Refund payment missing: " + claim.paymentId()));
                        compensationOperations.recordManualFollowUp(new RecordRefundCompensationDltCommand(
                                claim.requestId(), claim.paymentId(), payment.getOrderId().getValue(),
                                refundRequestedTopic, refundRequestedDltTopic, null, null,
                                ex.getClass().getName(), ex.getMessage()));
                    } catch (RuntimeException queueFailure) {
                        log.error("Could not queue failed provider refund paymentId={} requestId={}",
                                claim.paymentId(), claim.requestId(), queueFailure);
                    }
                } else {
                    try {
                        customerOperations.escalate(new EscalateCustomerRefundCommand(
                                claim.requestId(), claim.paymentId(), claim.providerRefundId(),
                                ex.getClass().getName(), ex.getMessage()));
                    } catch (RuntimeException queueFailure) {
                        log.error("Could not queue customer refund review paymentId={} refundId={}; reconciliation will retry",
                                claim.paymentId(), claim.requestId(), queueFailure);
                    }
                }
                log.error("Provider refund rejected paymentId={} requestId={} providerRefundId={}",
                        claim.paymentId(), claim.requestId(), claim.providerRefundId(), ex);
            } catch (RuntimeException ex) {
                log.warn("Provider refund reconciliation failed paymentId={} requestId={} providerRefundId={}",
                        claim.paymentId(), claim.requestId(), claim.providerRefundId(), ex);
            }
        }
    }
}

package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.port.output.PaymentRefundClaimPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundState;
import com.project.young.paymentservice.dataaccess.entity.PaymentRefundClaimEntity;
import com.project.young.paymentservice.dataaccess.repository.PaymentRefundClaimJpaRepository;
import com.project.young.paymentservice.domain.exception.PaymentRefundClaimConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class PaymentRefundClaimAdapter implements PaymentRefundClaimPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundClaimAdapter.class);
    private final PaymentRefundClaimJpaRepository repository;

    public PaymentRefundClaimAdapter(PaymentRefundClaimJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claimOrVerify(UUID paymentId, UUID requestId, Kind kind) {
        int inserted = repository.insertIfAbsent(paymentId, requestId, kind.name());
        findOwnedClaim(paymentId, requestId, kind);
        if (inserted == 1) {
            log.info("Claimed payment refund paymentId={} requestId={} kind={}", paymentId, requestId, kind);
        } else {
            log.debug("Reusing payment refund claim paymentId={} requestId={} kind={}", paymentId, requestId, kind);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RefundAttempt markAttemptStarted(UUID paymentId, UUID requestId, Kind kind, Instant now) {
        int started = repository.markAttemptStarted(paymentId, now);
        PaymentRefundClaimEntity claim = findOwnedClaim(paymentId, requestId, kind);
        Instant startedAt = claim.getFirstAttemptAt();
        if (startedAt == null) {
            throw new IllegalStateException("Missing first refund attempt time for payment " + paymentId);
        }
        log.debug("Refund attempt registered paymentId={} requestId={} firstAttempt={}",
                paymentId, requestId, started == 1);
        return new RefundAttempt(started == 1, startedAt, claim.getProviderRefundId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordProviderResult(UUID paymentId, UUID requestId, Kind kind,
                                     String providerRefundId, RefundState state) {
        if (repository.recordProviderResult(paymentId, requestId, kind.name(), providerRefundId, state.name(),
                Instant.now()) != 1) {
            PaymentRefundClaimEntity claim = findOwnedClaim(paymentId, requestId, kind);
            if (state == RefundState.PENDING && providerRefundId.equals(claim.getProviderRefundId())
                    && (RefundState.SUCCEEDED.name().equals(claim.getProviderRefundState())
                    || RefundState.FAILED.name().equals(claim.getProviderRefundState()))) {
                log.debug("Ignoring stale pending refund result paymentId={} requestId={} state={}",
                        paymentId, requestId, claim.getProviderRefundState());
                return;
            }
            throw new PaymentRefundClaimConflictException("Refund provider result conflicts with claim: " + paymentId);
        }
        log.info("Recorded provider refund state paymentId={} requestId={} providerRefundId={} state={}",
                paymentId, requestId, providerRefundId, state);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingRefund> findUnfinalized(int limit) {
        return repository.findUnfinalized(PageRequest.of(0, limit)).stream()
                .map(claim -> new PendingRefund(claim.getPaymentId(), claim.getRequestId(),
                        Kind.valueOf(claim.getRequestKind()), claim.getProviderRefundId()))
                .toList();
    }

    private PaymentRefundClaimEntity findOwnedClaim(UUID paymentId, UUID requestId, Kind kind) {
        PaymentRefundClaimEntity claim = repository.findById(paymentId).orElseThrow(() -> {
            log.error("Refund claim disappeared paymentId={} requestId={} kind={}", paymentId, requestId, kind);
            return new IllegalStateException("Refund claim is missing for payment " + paymentId);
        });
        if (!requestId.equals(claim.getRequestId())
                || !kind.name().equals(claim.getRequestKind())) {
            log.warn("Rejecting conflicting payment refund claim paymentId={} requestId={} kind={}",
                    paymentId, requestId, kind);
            throw new PaymentRefundClaimConflictException("Payment is already claimed by another refund request: " + paymentId);
        }
        return claim;
    }
}

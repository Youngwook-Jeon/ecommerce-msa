package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.EscalateCustomerRefundCommand;
import com.project.young.paymentservice.application.port.output.CustomerRefundReviewPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class CustomerRefundReviewApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CustomerRefundReviewApplicationService.class);
    private final CustomerRefundReviewPort reviews;

    public CustomerRefundReviewApplicationService(CustomerRefundReviewPort reviews) {
        this.reviews = reviews;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean escalate(EscalateCustomerRefundCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        boolean created = reviews.recordIfAbsent(command);
        log.warn("Escalated customer refund for operator review refundId={} paymentId={} providerRefundId={} newlyCreated={} cause={}",
                command.refundId(), command.paymentId(), command.providerRefundId(), created,
                command.failureExceptionClass());
        return created;
    }
}

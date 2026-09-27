package com.project.young.orderservice.application.support;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Starts a short local transaction only after remote Payment-service work has
 * completed. This keeps HTTP timeout/retry time out of the database connection
 * lifetime while preserving atomic refund and outbox persistence.
 */
@Component
public class CustomerRefundTxExecutor {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T executeInNewTransaction(Supplier<T> action) {
        return action.get();
    }
}

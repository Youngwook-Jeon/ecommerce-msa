package com.project.young.productservice.application.support;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Runs inventory mutation attempts in an isolated transaction so optimistic-lock
 * retries always start from a clean persistence context.
 */
@Component
public class InventoryReservationTxExecutor {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T executeInNewTransaction(Supplier<T> action) {
        return action.get();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void runInNewTransaction(Runnable action) {
        action.run();
    }
}

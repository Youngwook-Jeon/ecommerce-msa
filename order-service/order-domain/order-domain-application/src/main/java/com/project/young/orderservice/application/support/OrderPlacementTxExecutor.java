package com.project.young.orderservice.application.support;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Isolates local DB work from {@code OrderApplicationService} orchestration so that
 * external inventory calls stay outside the persistence transaction, and commit
 * failures surface to the caller (for compensation).
 */
@Component
public class OrderPlacementTxExecutor {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T executeInNewTransaction(Supplier<T> action) {
        return action.get();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void runInNewTransaction(Runnable action) {
        action.run();
    }
}

package com.project.young.productservice.messaging.publisher;

import com.project.young.common.domain.event.DomainEvent;
import com.project.young.common.domain.event.publisher.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class SpringEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public <T extends DomainEvent<?>> void publishEvent(T event) {
        log.debug("Publishing event: {}", event.getClass().getSimpleName());
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public <T extends DomainEvent<?>> void publishEventAfterCommit(T event) {
        log.debug("Publishing event after commit: {}", event.getClass().getSimpleName());
        applicationEventPublisher.publishEvent(new AfterCommitEvent<>(event));
    }

    public record AfterCommitEvent<T extends DomainEvent<?>>(T domainEvent) {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAfterCommitEvent(AfterCommitEvent<?> wrapperEvent) {
        publishEvent(wrapperEvent.domainEvent());
    }
}


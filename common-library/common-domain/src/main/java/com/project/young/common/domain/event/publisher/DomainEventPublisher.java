package com.project.young.common.domain.event.publisher;

import com.project.young.common.domain.event.DomainEvent;

public interface DomainEventPublisher {

    <T extends DomainEvent<?>> void publishEvent(T event);

    <T extends DomainEvent<?>> void publishEventAfterCommit(T event);
}


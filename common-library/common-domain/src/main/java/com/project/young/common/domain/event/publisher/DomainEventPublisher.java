package com.project.young.common.domain.event.publisher;

import com.project.young.common.domain.event.DomainEvent;

public interface DomainEventPublisher<T, R extends DomainEvent<T>>  {

    void publish(R domainEvent);
}

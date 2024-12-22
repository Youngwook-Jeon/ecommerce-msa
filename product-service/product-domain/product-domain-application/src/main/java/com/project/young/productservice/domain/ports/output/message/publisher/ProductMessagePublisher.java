package com.project.young.productservice.domain.ports.output.message.publisher;

import com.project.young.common.domain.event.publisher.DomainEventPublisher;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.event.ProductCreatedEvent;

public interface ProductMessagePublisher extends DomainEventPublisher<Product, ProductCreatedEvent> {
}

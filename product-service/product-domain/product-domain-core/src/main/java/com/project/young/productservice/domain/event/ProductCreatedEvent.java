package com.project.young.productservice.domain.event;

import com.project.young.common.domain.event.DomainEvent;
import com.project.young.productservice.domain.entity.Product;

import java.time.Instant;

public class ProductCreatedEvent implements DomainEvent<Product> {

    private final Product product;
    private final Instant createdAt;

    public ProductCreatedEvent(Product product, Instant createdAt) {
        this.product = product;
        this.createdAt = createdAt;
    }

    public Product getProduct() {
        return product;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

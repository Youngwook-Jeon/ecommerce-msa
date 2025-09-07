package com.project.young.productservice.domain.event;

import com.project.young.common.domain.event.DomainEvent;
import com.project.young.common.domain.valueobject.CategoryId;
import lombok.Getter;

import java.time.Instant;

@Getter
public class CategoryStatusChangedEvent implements DomainEvent<CategoryId> {

    private final CategoryId categoryId;
    private final String oldStatus;
    private final String newStatus;
    private final Instant occurredAt;

    public CategoryStatusChangedEvent(CategoryId categoryId, String oldStatus, String newStatus) {
        this.categoryId = categoryId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.occurredAt = Instant.now();
    }
}
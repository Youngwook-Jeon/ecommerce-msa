package com.project.young.productservice.domain.event;

import com.project.young.common.domain.event.DomainEvent;
import com.project.young.common.domain.valueobject.BrandId;
import lombok.Getter;

import java.time.Instant;

@Getter
public class BrandStatusChangedEvent implements DomainEvent<BrandId> {

    private final BrandId brandId;
    private final String oldStatus;
    private final String newStatus;
    private final Instant occurredAt;

    public BrandStatusChangedEvent(BrandId brandId, String oldStatus, String newStatus) {
        this.brandId = brandId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.occurredAt = Instant.now();
    }

}
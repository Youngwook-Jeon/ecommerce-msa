
package com.project.young.productservice.domain.event;

import com.project.young.common.domain.event.DomainEvent;
import com.project.young.common.domain.valueobject.BrandId;
import lombok.Getter;

import java.time.Instant;

@Getter
public class BrandDeletedEvent implements DomainEvent<BrandId> {

    private final BrandId brandId;
    private final String brandName;
    private final Instant occurredAt;

    public BrandDeletedEvent(BrandId brandId, String brandName) {
        this.brandId = brandId;
        this.brandName = brandName;
        this.occurredAt = Instant.now();
    }

}
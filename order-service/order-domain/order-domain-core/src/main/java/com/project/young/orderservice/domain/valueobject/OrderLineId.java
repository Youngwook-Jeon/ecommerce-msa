package com.project.young.orderservice.domain.valueobject;

import com.project.young.common.domain.valueobject.BaseId;

import java.util.UUID;

public class OrderLineId extends BaseId<UUID> {

    public OrderLineId(UUID value) {
        super(value);
    }
}

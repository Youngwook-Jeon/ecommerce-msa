package com.project.young.orderservice.domain.valueobject;

import com.project.young.common.domain.valueobject.BaseId;

import java.util.UUID;

public class OrderId extends BaseId<UUID> {

    public OrderId(UUID value) {
        super(value);
    }
}

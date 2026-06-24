package com.project.young.orderservice.domain.valueobject;

import com.project.young.common.domain.valueobject.BaseId;

import java.util.UUID;

public class CartId extends BaseId<UUID> {

    public CartId(UUID value) {
        super(value);
    }
}

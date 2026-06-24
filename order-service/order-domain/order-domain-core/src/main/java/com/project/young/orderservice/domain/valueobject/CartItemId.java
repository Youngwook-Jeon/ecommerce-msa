package com.project.young.orderservice.domain.valueobject;

import com.project.young.common.domain.valueobject.BaseId;

import java.util.UUID;

public class CartItemId extends BaseId<UUID> {

    public CartItemId(UUID value) {
        super(value);
    }
}

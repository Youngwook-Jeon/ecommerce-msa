package com.project.young.orderservice.domain.valueobject;

import com.project.young.common.domain.valueobject.BaseId;

import java.util.UUID;

public class CustomerRefundId extends BaseId<UUID> {

    public CustomerRefundId(UUID value) {
        super(value);
    }
}

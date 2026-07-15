package com.project.young.productservice.domain.valueobject;

import com.project.young.common.domain.valueobject.BaseId;

import java.util.UUID;

public class InventoryReservationId extends BaseId<UUID> {

    public InventoryReservationId(UUID value) {
        super(value);
    }
}

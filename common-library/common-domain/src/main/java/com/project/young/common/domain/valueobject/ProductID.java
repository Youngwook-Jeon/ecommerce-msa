package com.project.young.common.domain.valueobject;

import java.util.UUID;

public class ProductID extends BaseID<UUID> {
    public ProductID(UUID value) {
        super(value);
    }
}

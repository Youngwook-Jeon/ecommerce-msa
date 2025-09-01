package com.project.young.common.domain.valueobject;

import java.util.UUID;

public class BrandId extends BaseId<UUID> {
    public BrandId(UUID value) {
        super(value);
    }

    public BrandId() {
        super(UUID.randomUUID());
    }
}

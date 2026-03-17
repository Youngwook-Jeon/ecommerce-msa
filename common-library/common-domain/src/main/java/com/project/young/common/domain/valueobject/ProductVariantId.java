package com.project.young.common.domain.valueobject;

import java.util.UUID;

public class ProductVariantId extends BaseId<UUID> {
    public ProductVariantId(UUID value) {
        super(value);
    }
}

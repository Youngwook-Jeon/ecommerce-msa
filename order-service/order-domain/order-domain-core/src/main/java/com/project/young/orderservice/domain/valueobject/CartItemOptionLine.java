package com.project.young.orderservice.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record CartItemOptionLine(
        int stepOrder,
        UUID productOptionGroupId,
        String optionGroupName,
        UUID productOptionValueId,
        String optionValueName
) {

    public CartItemOptionLine {
        Objects.requireNonNull(productOptionGroupId, "productOptionGroupId must not be null");
        Objects.requireNonNull(optionGroupName, "optionGroupName must not be null");
        Objects.requireNonNull(productOptionValueId, "productOptionValueId must not be null");
        Objects.requireNonNull(optionValueName, "optionValueName must not be null");
        if (stepOrder < 0) {
            throw new IllegalArgumentException("stepOrder must not be negative");
        }
    }
}

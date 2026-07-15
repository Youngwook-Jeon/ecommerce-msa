package com.project.young.productservice.web.internal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public record ReserveInventoryRequest(
        @NotNull UUID checkoutId,
        @NotEmpty List<@Valid @NotNull Line> lines
) {
    public record Line(
            @NotNull UUID productVariantId,
            @Positive int quantity
    ) {
    }
}

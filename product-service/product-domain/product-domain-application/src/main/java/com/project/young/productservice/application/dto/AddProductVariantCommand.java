package com.project.young.productservice.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AddProductVariantCommand {

    @Min(value = 0, message = "Stock quantity cannot be negative.")
    private int stockQuantity;

    @NotEmpty(message = "Selected option values must not be empty.")
    private Set<@NotNull(message = "Selected option value id must not be null.") UUID> selectedProductOptionValueIds;
}

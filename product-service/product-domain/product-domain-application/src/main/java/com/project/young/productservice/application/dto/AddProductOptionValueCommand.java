package com.project.young.productservice.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AddProductOptionValueCommand {

    @NotNull(message = "OptionValue id must not be null.")
    private UUID optionValueId;

    @NotNull(message = "Price delta must not be null.")
    @DecimalMin(value = "0.00", inclusive = true, message = "Price delta must be greater than or equal to zero.")
    private BigDecimal priceDelta;

    @Builder.Default
    private boolean isDefault = false;

    @Builder.Default
    private boolean isActive = true;
}

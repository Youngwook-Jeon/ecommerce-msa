package com.project.young.productservice.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChangeProductOptionValuePriceDeltaCommand {

    @NotNull(message = "Price delta must not be null.")
    @DecimalMin(value = "0.00", inclusive = true, message = "Price delta must be greater than or equal to zero.")
    private BigDecimal priceDelta;
}

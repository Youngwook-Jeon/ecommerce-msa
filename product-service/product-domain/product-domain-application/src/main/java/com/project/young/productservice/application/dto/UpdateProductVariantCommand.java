package com.project.young.productservice.application.dto;

import com.project.young.productservice.domain.valueobject.ProductStatus;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateProductVariantCommand {

    @Min(value = 0, message = "Stock quantity cannot be negative.")
    private Integer stockQuantity;

    private ProductStatus status;
}

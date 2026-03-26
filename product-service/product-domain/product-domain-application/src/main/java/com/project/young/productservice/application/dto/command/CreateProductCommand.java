package com.project.young.productservice.application.dto.command;

import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateProductCommand {

    @NotBlank(message = "Product name cannot be blank.")
    @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters.")
    private String name;

    @NotBlank(message = "Product description cannot be blank.")
    @Size(min = 20, message = "Product description must be at least 20 characters.")
    private String description;

    @NotNull(message = "Base price must not be null.")
    @DecimalMin(value = "0.01", inclusive = true, message = "Base price must be greater than zero.")
    private BigDecimal basePrice;

    @NotBlank(message = "Brand cannot be blank.")
    @Size(min = 2, max = 100, message = "Brand must be between 2 and 100 characters.")
    private String brand;

    @NotBlank(message = "Main image URL cannot be blank.")
    @Size(max = 500, message = "Main image URL is too long.")
    private String mainImageUrl;

    @Positive(message = "Category id must be a positive number.")
    private Long categoryId;

    @NotNull(message = "Condition type must not be null.")
    private ConditionType conditionType;

    @NotNull(message = "Product status must not be null.")
    private ProductStatus status;
}
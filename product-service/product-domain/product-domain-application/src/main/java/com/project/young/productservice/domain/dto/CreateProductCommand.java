package com.project.young.productservice.domain.dto;

import jakarta.validation.constraints.*;
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

    @NotNull(message = "Name cannot be empty")
    @Size(min = 2, max = 50, message = "Product name must be between 2 and 50 characters")
    private String productName;

    @NotBlank(message = "Description cannot be empty")
    @Size(min = 10, max = 200, message = "Description must be between 2 and 50 characters")
    private String description;

    @NotNull(message = "Price cannot be empty")
    @DecimalMin(value = "0.00", message = "Price must be greater or equal than 0")
    @DecimalMax(value = "99999999.99", message = "Price is too high")
    @Digits(integer = 8, fraction = 2, message = "Not valid format of price")
    private BigDecimal price;
}

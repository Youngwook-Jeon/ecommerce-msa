package com.project.young.productservice.application.dto.product;

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
public class UpdateProductCommand {

    @NotBlank(message = "Product name cannot be blank.")
    @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters.")
    private String name;

    @NotBlank(message = "Description cannot be blank.")
    @Size(min = 10, max = 1000, message = "Description must be between 10 and 1000 characters.")
    private String description;

    @NotNull(message = "Base price cannot be null.")
    @DecimalMin(value = "0.00", message = "Base price must be greater than or equal to 0.")
    @DecimalMax(value = "99999999.99", message = "Base price is too high.")
    @Digits(integer = 8, fraction = 2, message = "Invalid price format.")
    private BigDecimal basePrice;

    @Positive(message = "Category ID must be a positive number.")
    private Long categoryId;

    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "Brand ID must be a valid UUID format.")
    private String brandId; // Optional UUID string

    @Pattern(regexp = "NEW|LIKE_NEW|GOOD|FAIR|POOR",
            message = "Condition type must be one of: NEW, LIKE_NEW, GOOD, FAIR, POOR.")
    private String conditionType;

    @Pattern(regexp = "ACTIVE|INACTIVE",
            message = "Status must be either ACTIVE or INACTIVE.")
    private String status;
}
package com.project.young.productservice.application.dto;

import com.project.young.productservice.domain.valueobject.CategoryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateCategoryCommand {

    @NotBlank(message = "Category name cannot be blank.")
    @Size(min = 2, max = 50, message = "Category name must be between 2 and 50 characters.")
    private String name;

    @Positive(message = "Parent category id must be a positive number.")
    private Long parentId;

    private CategoryStatus status;
}

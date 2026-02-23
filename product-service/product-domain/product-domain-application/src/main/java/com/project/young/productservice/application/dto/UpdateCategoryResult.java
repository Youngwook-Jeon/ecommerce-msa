package com.project.young.productservice.application.dto;

import com.project.young.productservice.domain.valueobject.CategoryStatus;
import lombok.Builder;

@Builder
public record UpdateCategoryResult(Long id, String name, Long parentId, CategoryStatus status) {
}

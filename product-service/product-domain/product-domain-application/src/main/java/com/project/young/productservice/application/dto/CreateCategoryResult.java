package com.project.young.productservice.application.dto;

import lombok.Builder;

@Builder
public record CreateCategoryResult(Long id, String name) {
}

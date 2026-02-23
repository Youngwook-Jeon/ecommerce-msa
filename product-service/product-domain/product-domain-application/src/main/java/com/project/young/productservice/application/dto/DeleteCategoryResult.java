package com.project.young.productservice.application.dto;

import lombok.Builder;

@Builder
public record DeleteCategoryResult(Long id, String name) {
}

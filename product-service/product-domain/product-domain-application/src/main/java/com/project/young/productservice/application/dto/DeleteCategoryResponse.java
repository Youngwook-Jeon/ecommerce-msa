package com.project.young.productservice.application.dto;

import lombok.Builder;

@Builder
public record DeleteCategoryResponse(Long id, String message) {
}

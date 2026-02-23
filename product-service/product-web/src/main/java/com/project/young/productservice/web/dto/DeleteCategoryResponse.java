package com.project.young.productservice.web.dto;

import lombok.Builder;

@Builder
public record DeleteCategoryResponse(Long id, String name, String message) {
}

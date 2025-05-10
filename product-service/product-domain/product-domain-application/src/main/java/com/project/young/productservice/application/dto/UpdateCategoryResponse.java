package com.project.young.productservice.application.dto;

import lombok.Builder;

@Builder
public record UpdateCategoryResponse(String name, String message) {
}

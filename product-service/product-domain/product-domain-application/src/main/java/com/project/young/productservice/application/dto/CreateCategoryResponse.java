package com.project.young.productservice.application.dto;

import lombok.Builder;

@Builder
public record CreateCategoryResponse(String name,String message) {
}

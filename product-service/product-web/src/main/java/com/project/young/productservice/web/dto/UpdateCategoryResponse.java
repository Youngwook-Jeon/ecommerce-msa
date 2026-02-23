package com.project.young.productservice.web.dto;

import lombok.Builder;

@Builder
public record UpdateCategoryResponse(Long id, String name, Long parentId, String status, String message) {
}

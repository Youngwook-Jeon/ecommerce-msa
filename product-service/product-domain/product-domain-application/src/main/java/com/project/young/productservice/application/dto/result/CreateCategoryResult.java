package com.project.young.productservice.application.dto.result;

import lombok.Builder;

@Builder
public record CreateCategoryResult(Long id, String name) {
}

package com.project.young.productservice.application.dto.result;

import lombok.Builder;

@Builder
public record DeleteCategoryResult(Long id, String name) {
}

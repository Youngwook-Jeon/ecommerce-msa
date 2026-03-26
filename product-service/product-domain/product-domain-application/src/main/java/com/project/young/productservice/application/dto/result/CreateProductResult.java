package com.project.young.productservice.application.dto.result;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CreateProductResult(
        UUID id,
        String name
) {
}
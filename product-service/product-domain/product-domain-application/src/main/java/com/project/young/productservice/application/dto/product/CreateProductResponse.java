package com.project.young.productservice.application.dto.product;

import lombok.Builder;

@Builder
public record CreateProductResponse(
        String productId,
        String name,
        String message
) {}
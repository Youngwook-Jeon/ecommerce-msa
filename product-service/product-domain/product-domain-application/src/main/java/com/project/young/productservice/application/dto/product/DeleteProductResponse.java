package com.project.young.productservice.application.dto.product;

import lombok.Builder;

@Builder
public record DeleteProductResponse(
        String productId,
        String name,
        String message
) {}
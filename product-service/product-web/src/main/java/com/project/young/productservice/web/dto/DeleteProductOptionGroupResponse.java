package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record DeleteProductOptionGroupResponse(
        UUID productId,
        UUID productOptionGroupId,
        String status,
        double stepOrder,
        String message
) {
}


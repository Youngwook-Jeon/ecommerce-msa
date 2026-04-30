package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ChangeProductOptionGroupStepOrderResponse(
        UUID productId,
        UUID productOptionGroupId,
        double stepOrder,
        String message
) {
}


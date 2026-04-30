package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ReorderProductOptionGroupsResponse(
        UUID productId,
        int updatedCount,
        String message
) {
}


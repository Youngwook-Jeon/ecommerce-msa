package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ProductImageReorderResponse(
        UUID productId,
        int reorderedCount,
        List<UUID> orderedImageIds,
        String message
) {
}

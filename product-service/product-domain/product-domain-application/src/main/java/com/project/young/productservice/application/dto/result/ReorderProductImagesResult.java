package com.project.young.productservice.application.dto.result;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ReorderProductImagesResult(
        UUID productId,
        int reorderedCount,
        List<UUID> orderedImageIds
) {
}

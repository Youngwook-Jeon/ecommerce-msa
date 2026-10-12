package com.project.young.productservice.web.dto;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminProductSearchIndexBuildJobResponse(
        UUID id, UUID indexVersionId, int batchSize, ProductSearchIndexBuildStatus status,
        Progress progress, String requestedBy, Instant createdAt, Instant updatedAt,
        Instant startedAt, Instant finishedAt, Failure failure
) {
    public record Progress(Long sourceProductCount, long indexedProducts, long completedBatches) {
    }

    public record Failure(String code, String message) {
    }
}

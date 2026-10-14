package com.project.young.productservice.web.dto;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminProductSearchIndexActivationJobResponse(UUID id, UUID indexVersionId, UUID expectedActiveVersionId,
        String aliasName, ProductSearchIndexActivationStatus status, String requestedBy, Instant createdAt, Instant updatedAt,
        Instant startedAt, Instant switchStartedAt, Instant finishedAt, Failure failure) {
    public record Failure(String code, String message) { }
}

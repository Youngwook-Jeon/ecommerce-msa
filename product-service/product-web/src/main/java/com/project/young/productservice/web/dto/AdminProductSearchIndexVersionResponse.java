package com.project.young.productservice.web.dto;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminProductSearchIndexVersionResponse(
        UUID id, long versionNumber, String indexName, int mappingVersion, String mappingHash,
        ProductSearchIndexStatus status, String requestedBy, Instant createdAt, Instant updatedAt, Instant activatedAt
) {
}

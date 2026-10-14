package com.project.young.productservice.web.dto;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexAliasObservation;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexReconciliationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminProductSearchIndexReconciliationResponse(UUID id, UUID jobId, String trigger, String requestedBy,
        ProductSearchIndexReconciliationStatus status, Instant createdAt, Instant finishedAt,
        ProductSearchIndexAliasObservation observation, List<String> observedIndices, Boolean aliasPlain, Failure failure) {
    public record Failure(String code, String message) { }
}

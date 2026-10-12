package com.project.young.productservice.web.dto;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildStatus;

import java.util.UUID;

public record AdminProductSearchIndexBuildAcceptedResponse(UUID jobId, UUID indexVersionId,
                                                           ProductSearchIndexBuildStatus status) {
}

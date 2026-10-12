package com.project.young.productservice.web.dto;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexStatus;

import java.util.UUID;

/** Acceptance of alias-switch intent, not confirmation that the alias has switched. */
public record AdminProductSearchIndexActivationResponse(UUID indexVersionId, UUID expectedActiveVersionId,
                                                        ProductSearchIndexStatus status) {
}

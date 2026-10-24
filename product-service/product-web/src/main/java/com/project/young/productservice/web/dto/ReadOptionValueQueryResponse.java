package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ReadOptionValueQueryResponse(
        UUID id,
        UUID attributeValueId,
        String value,
        String displayName,
        int sortOrder,
        String status
) {
}

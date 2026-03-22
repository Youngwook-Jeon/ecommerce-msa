package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ReadOptionGroupQueryResponse(
        UUID id,
        String name,
        String displayName,
        String status,
        List<ReadOptionValueQueryResponse> optionValues
) {
    public ReadOptionGroupQueryResponse {
        optionValues = optionValues == null ? List.of() : optionValues;
    }
}

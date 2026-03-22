package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ReadOptionGroupListQueryResponse(List<ReadOptionGroupQueryResponse> optionGroups) {
    public ReadOptionGroupListQueryResponse {
        optionGroups = optionGroups == null ? List.of() : optionGroups;
    }
}

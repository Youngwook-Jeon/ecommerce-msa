package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ReadProductOptionGroupResponse(
        UUID productOptionGroupId,
        UUID optionGroupId,
        int stepOrder,
        boolean required,
        List<ReadProductOptionValueResponse> optionValues
) {
    public ReadProductOptionGroupResponse {
        optionValues = optionValues == null ? List.of() : optionValues;
    }
}

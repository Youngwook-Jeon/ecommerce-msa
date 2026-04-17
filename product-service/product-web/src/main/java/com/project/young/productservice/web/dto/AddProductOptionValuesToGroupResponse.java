package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.List;
import java.util.Objects;

@Builder
public record AddProductOptionValuesToGroupResponse(
        List<AddProductOptionValueToGroupResponse> optionValues
) {
    public AddProductOptionValuesToGroupResponse {
        optionValues = Objects.requireNonNull(optionValues, "optionValues must not be null");
    }
}


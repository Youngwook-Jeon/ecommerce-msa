package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.List;
import java.util.Objects;

@Builder
public record AddOptionValuesResponse(
        List<AddOptionValueResponse> optionValues
) {
    public AddOptionValuesResponse {
        optionValues = Objects.requireNonNull(optionValues, "optionValues must not be null");
    }
}


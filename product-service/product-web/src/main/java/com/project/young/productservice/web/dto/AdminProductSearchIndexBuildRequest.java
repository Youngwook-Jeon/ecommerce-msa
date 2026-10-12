package com.project.young.productservice.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdminProductSearchIndexBuildRequest(
        @Min(1) @Max(1000) Integer batchSize,
        @NotNull @AssertTrue Boolean catalogWritesPaused
) {
    public AdminProductSearchIndexBuildRequest {
        batchSize = batchSize == null ? 500 : batchSize;
    }
}

package com.project.young.productservice.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdminProductSearchIndexActivationRequest(
        UUID expectedActiveVersionId,
        @NotNull @AssertTrue Boolean catalogWritesPaused
) {
}

package com.project.young.paymentservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManualOrderCreatedDltResolutionRequest(
        @NotBlank @Size(max = 4000) String reason
) {
}

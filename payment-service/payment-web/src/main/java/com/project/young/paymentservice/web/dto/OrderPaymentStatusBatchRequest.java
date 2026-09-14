package com.project.young.paymentservice.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record OrderPaymentStatusBatchRequest(
        @NotEmpty @Size(max = 100) List<@NotNull UUID> orderIds
) {
}

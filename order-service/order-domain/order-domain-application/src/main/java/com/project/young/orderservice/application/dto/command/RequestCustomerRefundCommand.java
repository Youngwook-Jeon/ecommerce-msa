package com.project.young.orderservice.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RequestCustomerRefundCommand(
        @NotNull UUID orderId,
        @NotBlank @Size(max = 512) String reason
) {
}

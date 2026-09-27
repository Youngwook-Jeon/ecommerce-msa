package com.project.young.orderservice.web.refund.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestCustomerRefundRequest(
        @NotBlank @Size(max = 512) String reason
) {
}

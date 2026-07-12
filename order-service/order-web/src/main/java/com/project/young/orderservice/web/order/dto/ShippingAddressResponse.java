package com.project.young.orderservice.web.order.dto;

import lombok.Builder;

@Builder
public record ShippingAddressResponse(
        String recipientName,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String postalCode,
        String countryCode
) {
}

package com.project.young.orderservice.domain.valueobject;

import java.util.Objects;

public record ShippingAddress(
        String recipientName,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String postalCode,
        String countryCode
) {

    public ShippingAddress {
        Objects.requireNonNull(recipientName, "recipientName must not be null");
        Objects.requireNonNull(phone, "phone must not be null");
        Objects.requireNonNull(addressLine1, "addressLine1 must not be null");
        Objects.requireNonNull(city, "city must not be null");
        Objects.requireNonNull(postalCode, "postalCode must not be null");
        Objects.requireNonNull(countryCode, "countryCode must not be null");

        if (recipientName.isBlank()) {
            throw new IllegalArgumentException("recipientName must not be blank");
        }
        if (phone.isBlank()) {
            throw new IllegalArgumentException("phone must not be blank");
        }
        if (addressLine1.isBlank()) {
            throw new IllegalArgumentException("addressLine1 must not be blank");
        }
        if (city.isBlank()) {
            throw new IllegalArgumentException("city must not be blank");
        }
        if (postalCode.isBlank()) {
            throw new IllegalArgumentException("postalCode must not be blank");
        }
        if (countryCode.isBlank() || countryCode.length() != 2) {
            throw new IllegalArgumentException("countryCode must be a 2-letter ISO code");
        }
    }
}

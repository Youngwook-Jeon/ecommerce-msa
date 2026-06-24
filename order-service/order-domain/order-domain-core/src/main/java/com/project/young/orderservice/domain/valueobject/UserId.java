package com.project.young.orderservice.domain.valueobject;

import java.util.Objects;

public record UserId(String value) {

    public UserId {
        Objects.requireNonNull(value, "userId must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
    }
}

package com.project.young.orderservice.web.converter;

import com.project.young.orderservice.domain.valueobject.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class OrderStatusWebConverter {

    public String toStringValue(OrderStatus status) {
        Objects.requireNonNull(status, "OrderStatus cannot be null");
        return status.name();
    }
}

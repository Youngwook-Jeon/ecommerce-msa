package com.project.young.productservice.web.converter;

import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ProductStatusWebConverter {

    public String toStringValue(ProductStatus status) {
        Objects.requireNonNull(status, "ProductStatus cannot be null");
        return status.name();
    }
}
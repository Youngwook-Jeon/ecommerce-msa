package com.project.young.productservice.web.converter;

import com.project.young.productservice.domain.valueobject.CategoryStatus;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CategoryStatusWebConverter {

    public String toStringValue(CategoryStatus status) {
        Objects.requireNonNull(status, "CategoryStatus cannot be null");
        return status.name();
    }
}
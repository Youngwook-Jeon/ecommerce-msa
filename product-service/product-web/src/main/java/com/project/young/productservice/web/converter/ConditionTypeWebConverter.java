package com.project.young.productservice.web.converter;

import com.project.young.productservice.domain.valueobject.ConditionType;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ConditionTypeWebConverter {

    public String toStringValue(ConditionType type) {
        Objects.requireNonNull(type, "ConditionType cannot be null");
        return type.name();
    }
}

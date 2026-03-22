package com.project.young.productservice.web.converter;

import com.project.young.productservice.domain.valueobject.OptionStatus;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class OptionStatusWebConverter {

    public String toStringValue(OptionStatus status) {
        Objects.requireNonNull(status, "OptionStatus cannot be null");
        return status.name();
    }
}
package com.project.young.productservice.application.dto.result;

import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.valueobject.CategoryAttributePolicy;

public record CategoryAttributeResult(AttributeDefinition attribute, CategoryAttributePolicy policy) {
    public boolean usable() {
        return attribute.isActive() && policy.enabled();
    }

    public boolean optionAvailable() {
        return usable() && policy.optionAllowed();
    }

    public boolean filterAvailable() {
        return usable() && policy.filterable();
    }
}

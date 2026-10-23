package com.project.young.productservice.domain.valueobject;

import com.project.young.productservice.domain.exception.AttributeDomainException;

import java.util.UUID;

/** An explicit attribute policy that applies only to the specified category. */
public record CategoryAttributePolicy(Long categoryId, UUID attributeId, boolean enabled, boolean required,
                                      boolean optionAllowed, boolean filterable, int displayOrder) {
    public CategoryAttributePolicy {
        if (categoryId == null || categoryId < 1 || attributeId == null || displayOrder < 0) {
            throw new AttributeDomainException("A positive category ID, attribute ID and nonnegative display order are required");
        }
        if (!enabled && (required || optionAllowed || filterable)) {
            throw new AttributeDomainException("Disabled attributes cannot be required, selectable or filterable");
        }
    }
}

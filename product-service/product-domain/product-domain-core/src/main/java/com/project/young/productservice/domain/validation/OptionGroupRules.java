package com.project.young.productservice.domain.validation;

import com.project.young.productservice.domain.exception.OptionDomainException;

public final class OptionGroupRules {
    private OptionGroupRules() {
    }

    public static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new OptionDomainException("Option group name cannot be null or blank.");
        }
        if (name.length() > 100) {
            throw new OptionDomainException("Option group name must be 100 characters or less.");
        }
    }

    public static void validateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new OptionDomainException("Option group display name cannot be null or blank.");
        }
        if (displayName.length() > 100) {
            throw new OptionDomainException("Option group display name must be 100 characters or less.");
        }
    }
}

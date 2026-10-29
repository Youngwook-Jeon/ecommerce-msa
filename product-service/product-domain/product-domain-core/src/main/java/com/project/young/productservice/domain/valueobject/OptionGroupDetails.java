package com.project.young.productservice.domain.valueobject;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.domain.exception.OptionDomainException;

import static com.project.young.productservice.domain.validation.OptionGroupRules.validateDisplayName;
import static com.project.young.productservice.domain.validation.OptionGroupRules.validateName;

/** Parent-only state for information edits; it makes no claim about the group's option values. */
public record OptionGroupDetails(OptionGroupId id, String name, String displayName, OptionStatus status, long version) {
    public OptionGroupDetails {
        if (id == null || id.getValue() == null || status == null) {
            throw new OptionDomainException("Option group ID and status are required.");
        }
        if (version < 0) {
            throw new OptionDomainException("Option group version must not be negative.");
        }
        validateName(name);
        validateDisplayName(displayName);
    }

    public void requireMutable() {
        if (status.isDeleted()) {
            throw new OptionDomainException("Cannot modify an option group that has been deleted.");
        }
    }

    public OptionGroupDetails rename(String newName) {
        requireMutable();
        return new OptionGroupDetails(id, newName, displayName, status, version);
    }

    public OptionGroupDetails changeDisplayName(String newDisplayName) {
        requireMutable();
        return new OptionGroupDetails(id, name, newDisplayName, status, version);
    }

    public OptionGroupDetails changeStatus(OptionStatus newStatus) {
        requireMutable();
        if (!status.canTransitionTo(newStatus)) {
            throw new OptionDomainException("Invalid status provided for update: " + newStatus);
        }
        return new OptionGroupDetails(id, name, displayName, newStatus, version);
    }
}

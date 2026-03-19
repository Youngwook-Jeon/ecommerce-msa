package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.BaseEntity;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.domain.exception.OptionDomainException;

import com.project.young.productservice.domain.valueobject.OptionStatus;
import lombok.Getter;

@Getter
public class OptionValue extends BaseEntity<OptionValueId> {

    // 내부 식별용 고유 값 (예: "RED", "16GB").
    private String value;

    // 사용자 노출용 이름 (예: "빨강", "16기가바이트").
    private String displayName;

    private int sortOrder;

    private OptionStatus status;

    public static Builder builder() {
        return new Builder();
    }

    private OptionValue(Builder builder) {
        super.setId(builder.id);
        this.value = builder.value;
        this.displayName = builder.displayName;
        this.sortOrder = builder.sortOrder;
        this.status = builder.status;
    }

    private OptionValue(OptionValueId id, String value, String displayName, int sortOrder, OptionStatus status) {
        super.setId(id);
        this.value = value;
        this.displayName = displayName;
        this.sortOrder = sortOrder;
        this.status = status;
    }

    // ========================================================================
    // 비즈니스 로직
    // ========================================================================

    public boolean isDeleted() {
        return this.status.isDeleted();
    }

    void changeValue(String newValue) {
        if (isDeleted()) {
            throw new OptionDomainException("Cannot change the value of a deleted option value.");
        }
        validateValue(newValue);
        this.value = newValue;
    }

    void changeDisplayName(String newDisplayName) {
        if (isDeleted()) {
            throw new OptionDomainException("Cannot change the display name of a deleted option value.");
        }
        validateDisplayName(newDisplayName);
        this.displayName = newDisplayName;
    }

    void changeSortOrder(int newSortOrder) {
        if (isDeleted()) {
            throw new OptionDomainException("Cannot change the sort order of a deleted option value.");
        }
        this.sortOrder = newSortOrder;
    }

    void changeStatus(OptionStatus newStatus) {
        if (isDeleted()) {
            throw new OptionDomainException("Cannot change the status of a deleted option value.");
        }
        if (!this.status.canTransitionTo(newStatus)) {
            throw new OptionDomainException("Invalid status provided for update: " + newStatus);
        }
        this.status = newStatus;
    }

    void markAsDeleted() {
        if (isDeleted()) {
            return;
        }
        this.status = OptionStatus.DELETED;
    }

    // ========================================================================
    // 검증 로직 (Validation)
    // ========================================================================

    private static void validateValue(String value) {
        if (value == null || value.isBlank()) {
            throw new OptionDomainException("Option value string cannot be null or blank.");
        }
        if (value.length() > 100) {
            throw new OptionDomainException("Option value string must be 100 characters or less.");
        }
    }

    private static void validateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new OptionDomainException("Option value display name cannot be null or blank.");
        }
        if (displayName.length() > 100) {
            throw new OptionDomainException("Option value display name must be 100 characters or less.");
        }
    }

    // ========================================================================
    // FOR PERSISTENCE MAPPING ONLY
    // ========================================================================
    public static OptionValue reconstitute(OptionValueId id, String value, String displayName, int sortOrder, OptionStatus status) {
        return new OptionValue(id, value, displayName, sortOrder, status);
    }

    // ========================================================================
    // Builder
    // ========================================================================
    public static class Builder {
        private OptionValueId id;
        private String value;
        private String displayName;
        private int sortOrder = 0;
        private OptionStatus status = OptionStatus.ACTIVE;

        public Builder id(OptionValueId id) {
            this.id = id;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder sortOrder(int sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        public Builder status(OptionStatus status) {
            this.status = status;
            return this;
        }

        public OptionValue build() {
            validate();
            return new OptionValue(this);
        }

        private void validate() {
            validateValue(this.value);
            validateDisplayName(this.displayName);
            if (status == null) {
                throw new OptionDomainException("Option status cannot be null.");
            }
            if (status.isDeleted()) {
                throw new OptionDomainException("Option value must not be created with DELETED status.");
            }
        }
    }
}
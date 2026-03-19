package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.domain.exception.OptionDomainException;

import com.project.young.productservice.domain.valueobject.OptionStatus;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class OptionGroup extends AggregateRoot<OptionGroupId> {

    // 내부 관리용 고유 이름 (예: "COLOR", "STORAGE").
    private String name;

    // 사용자 노출용 이름 (예: "색상", "저장용량").
    private String displayName;

    private OptionStatus status;

    // 자식 엔티티 컬렉션
    private final List<OptionValue> optionValues;

    public static Builder builder() {
        return new Builder();
    }

    private OptionGroup(Builder builder) {
        super.setId(builder.id);
        this.name = builder.name;
        this.displayName = builder.displayName;
        this.status = builder.status;
        this.optionValues = builder.optionValues != null ? builder.optionValues : new ArrayList<>();
    }

    private OptionGroup(OptionGroupId id, String name, String displayName, OptionStatus status, List<OptionValue> optionValues) {
        super.setId(id);
        this.name = name;
        this.displayName = displayName;
        this.status = status;
        this.optionValues = optionValues != null ? new ArrayList<>(optionValues) : new ArrayList<>();
    }

    public List<OptionValue> getOptionValues() {
        return List.copyOf(this.optionValues);
    }

    public OptionValue getOptionValue(OptionValueId optionValueId) {
        return this.optionValues.stream()
                .filter(v -> v.getId().equals(optionValueId))
                .findFirst()
                .orElseThrow(() -> new OptionDomainException("Option value not found in this group."));
    }

    // ========================================================================
    // Aggregate Root 비즈니스 로직
    // ========================================================================
    public void changeName(String newName) {
        if (isDeleted()) {
            throw new OptionDomainException("Cannot change the name of a deleted option group.");
        }
        validateName(newName);
        this.name = newName;
    }

    public void changeDisplayName(String newDisplayName) {
        if (isDeleted()) {
            throw new OptionDomainException("Cannot change the display name of a deleted option group.");
        }
        validateDisplayName(newDisplayName);
        this.displayName = newDisplayName;
    }

    public void changeStatus(OptionStatus newStatus) {
        if (isDeleted()) {
            throw new OptionDomainException("Cannot change the status of a deleted option group.");
        }
        if (!this.status.canTransitionTo(newStatus)) {
            throw new OptionDomainException("Invalid status provided for update: " + newStatus);
        }
        this.status = newStatus;
    }

    public void addOptionValue(OptionValue newValue) {
        if (isDeleted()) {
            throw new OptionDomainException("Cannot add option value to a deleted option group.");
        }
        // 데이터 정합성 방어: 동일한 내부 value(예: "RED")가 이미 존재하는지 검증
        boolean valueExists = this.optionValues.stream()
                .anyMatch(v -> v.getValue().equalsIgnoreCase(newValue.getValue()));

        if (valueExists) {
            throw new OptionDomainException(
                    String.format("Option value '%s' already exists in group '%s'", newValue.getValue(), this.name)
            );
        }

        this.optionValues.add(newValue);
    }

    public void updateOptionValueDetails(OptionValueId optionValueId,
                                         String newValue,
                                         String newDisplayName,
                                         Integer newSortOrder,
                                         OptionStatus newStatus) {
        if (isDeleted()) {
            throw new OptionDomainException("Cannot update option value in a deleted option group.");
        }

        OptionValue targetValue = this.optionValues.stream()
                .filter(v -> v.getId().equals(optionValueId))
                .findFirst()
                .orElseThrow(() -> new OptionDomainException("Option value not found in this group."));

        // 1. 내부 식별용 값 (value) 변경 및 중복 검사
        if (newValue != null && !newValue.equals(targetValue.getValue())) {
            // 이 그룹 내에 변경하려는 value와 똑같은 값을 가진 다른 OptionValue가 있는지 확인
            boolean valueExists = this.optionValues.stream()
                    .filter(v -> !v.getId().equals(optionValueId)) // 자기 자신은 제외
                    .anyMatch(v -> v.getValue().equalsIgnoreCase(newValue));

            if (valueExists) {
                throw new OptionDomainException(
                        String.format("Option value '%s' already exists in group '%s'", newValue, this.name)
                );
            }
            targetValue.changeValue(newValue);
        }

        // 2. 사용자 노출용 이름 (displayName) 변경
        if (newDisplayName != null && !newDisplayName.equals(targetValue.getDisplayName())) {
            targetValue.changeDisplayName(newDisplayName);
        }

        // 3. 정렬 순서 (sortOrder) 변경
        if (newSortOrder != null && newSortOrder != targetValue.getSortOrder()) {
            targetValue.changeSortOrder(newSortOrder);
        }

        // 4. 상태 (status) 변경
        if (newStatus != null && newStatus != targetValue.getStatus()) {
            targetValue.changeStatus(newStatus);
        }
    }

    public void deleteOptionValue(OptionValueId optionValueIdToDelete) {
        if (isDeleted()) {
            throw new OptionDomainException("Cannot delete option value from a deleted option group.");
        }

        OptionValue targetValue = this.optionValues.stream()
                .filter(v -> v.getId().equals(optionValueIdToDelete))
                .findFirst()
                .orElseThrow(() -> new OptionDomainException("Option value not found in this group."));

        targetValue.markAsDeleted();
    }

    public boolean isDeleted() {
        return this.status.isDeleted();
    }

    public void markAsDeleted() {
        if (isDeleted()) {
            return;
        }
        this.status = OptionStatus.DELETED;
        this.optionValues.forEach(OptionValue::markAsDeleted);
    }

    // ========================================================================
    // 검증 로직 (Validation)
    // ========================================================================

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new OptionDomainException("Option group name cannot be null or blank.");
        }
        if (name.length() > 100) {
            throw new OptionDomainException("Option group name must be 100 characters or less.");
        }
    }

    private static void validateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new OptionDomainException("Option group display name cannot be null or blank.");
        }
        if (displayName.length() > 100) {
            throw new OptionDomainException("Option group display name must be 100 characters or less.");
        }
    }

    // ========================================================================
    // FOR PERSISTENCE MAPPING ONLY
    // ========================================================================
    public static OptionGroup reconstitute(OptionGroupId id, String name, String displayName, OptionStatus status, List<OptionValue> optionValues) {
        return new OptionGroup(id, name, displayName, status, optionValues);
    }

    // ========================================================================
    // Builder
    // ========================================================================
    public static class Builder {
        private OptionGroupId id;
        private String name;
        private String displayName;
        private OptionStatus status = OptionStatus.ACTIVE;
        private List<OptionValue> optionValues = new ArrayList<>();

        public Builder id(OptionGroupId id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder status(OptionStatus status) {
            this.status = status;
            return this;
        }

        public Builder optionValues(List<OptionValue> optionValues) {
            this.optionValues = optionValues;
            return this;
        }

        public OptionGroup build() {
            validate();
            return new OptionGroup(this);
        }

        private void validate() {
            validateName(this.name);
            validateDisplayName(this.displayName);
            if (status == null) {
                throw new OptionDomainException("Option status cannot be null.");
            }
            if (status.isDeleted()) {
                throw new OptionDomainException("Option group must not be created with DELETED status.");
            }
        }
    }
}
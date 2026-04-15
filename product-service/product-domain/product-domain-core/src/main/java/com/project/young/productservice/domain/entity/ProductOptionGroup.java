package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.BaseEntity;
import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.common.domain.valueobject.ProductOptionGroupId;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.valueobject.OptionStatus;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ProductOptionGroup extends BaseEntity<ProductOptionGroupId> {

    // 글로벌 옵션 그룹에 대한 식별자 참조 (예: 'COLOR'의 글로벌 ID). 생성 후 변경 불가.
    private final OptionGroupId optionGroupId;

    // 프론트엔드에서 노출될 단계/순서 (1, 2, 3...)
    private int stepOrder;

    // 고객이 반드시 선택해야 하는 필수 옵션인지 여부
    private boolean isRequired;

    // 이 상품에서 옵션 그룹의 로컬 상태
    private OptionStatus status;

    // 하위 옵션 값 컬렉션 (ProductOptionValue)
    private final List<ProductOptionValue> optionValues;

    public static Builder builder() {
        return new Builder();
    }

    private ProductOptionGroup(Builder builder) {
        super.setId(builder.id);
        this.optionGroupId = builder.optionGroupId;
        this.stepOrder = builder.stepOrder;
        this.isRequired = builder.isRequired;
        this.status = builder.status != null ? builder.status : OptionStatus.ACTIVE;
        this.optionValues = builder.optionValues != null ? builder.optionValues : new ArrayList<>();
    }

    private ProductOptionGroup(ProductOptionGroupId id, OptionGroupId optionGroupId, int stepOrder, boolean isRequired, OptionStatus status, List<ProductOptionValue> optionValues) {
        super.setId(id);
        this.optionGroupId = optionGroupId;
        this.stepOrder = stepOrder;
        this.isRequired = isRequired;
        this.status = status != null ? status : OptionStatus.ACTIVE;
        this.optionValues = optionValues != null ? new ArrayList<>(optionValues) : new ArrayList<>();
    }

    public List<ProductOptionValue> getOptionValues() {
        if (this.optionValues == null || this.optionValues.isEmpty()) {
            return List.of();
        }
        return List.copyOf(this.optionValues);
    }

    // ========================================================================
    // 비즈니스 로직
    // ========================================================================

    void changeStepOrder(int newStepOrder) {
        validateStepOrder(newStepOrder);
        this.stepOrder = newStepOrder;
    }

    void changeRequiredStatus(boolean isRequired) {
        this.isRequired = isRequired;
    }

    void changeStatus(OptionStatus newStatus) {
        if (newStatus == null) {
            throw new ProductDomainException("Option group status cannot be null.");
        }
        if (!this.status.canTransitionTo(newStatus)) {
            throw new ProductDomainException("Invalid option group status transition: " + this.status + " -> " + newStatus);
        }
        this.status = newStatus;
    }

    void deactivateGroup() {
        if (this.status == OptionStatus.DELETED) {
            return;
        }
        this.status = OptionStatus.INACTIVE;
    }

    void activateGroup() {
        if (this.status == OptionStatus.DELETED) {
            throw new ProductDomainException("Cannot activate a deleted option group.");
        }
        this.status = OptionStatus.ACTIVE;
    }

    public boolean isActive() {
        return this.status != null && this.status.isActive();
    }

    void addOptionValue(ProductOptionValue newValue) {
        // 데이터 정합성 방어: 이 로컬 그룹에 이미 동일한 글로벌 옵션 값이 들어있는지 확인
        boolean valueExists = this.optionValues.stream()
                .anyMatch(v -> v.getOptionValueId().equals(newValue.getOptionValueId()));

        if (valueExists) {
            throw new ProductDomainException("Product option value already exists in this group.");
        }

        this.optionValues.add(newValue);
    }

    void deactivateOptionValue(OptionValueId optionValueIdToRemove) {
        ProductOptionValue targetValue = this.optionValues.stream()
                .filter(v -> v.getOptionValueId().equals(optionValueIdToRemove))
                .findFirst()
                .orElseThrow(() -> new ProductDomainException("해당 옵션 값이 이 그룹에 존재하지 않습니다."));

        targetValue.deactivateLocalOption();
    }

    // ========================================================================
    // 검증 로직
    // ========================================================================

    private static void validateStepOrder(int stepOrder) {
        if (stepOrder <= 0) {
            throw new ProductDomainException("Step order must be greater than zero.");
        }
    }

    // ========================================================================
    // FOR PERSISTENCE MAPPING ONLY
    // ========================================================================
    public static ProductOptionGroup reconstitute(ProductOptionGroupId id, OptionGroupId optionGroupId, int stepOrder, boolean isRequired, OptionStatus status, List<ProductOptionValue> optionValues) {
        return new ProductOptionGroup(id, optionGroupId, stepOrder, isRequired, status, optionValues);
    }

    public static ProductOptionGroup reconstitute(ProductOptionGroupId id, OptionGroupId optionGroupId, int stepOrder, boolean isRequired, List<ProductOptionValue> optionValues) {
        return new ProductOptionGroup(id, optionGroupId, stepOrder, isRequired, OptionStatus.ACTIVE, optionValues);
    }

    // ========================================================================
    // Builder
    // ========================================================================
    public static class Builder {
        private ProductOptionGroupId id;
        private OptionGroupId optionGroupId;
        private int stepOrder;
        private boolean isRequired = true; // 기본값: 필수 옵션
        private OptionStatus status = OptionStatus.ACTIVE;
        private List<ProductOptionValue> optionValues = new ArrayList<>();

        public Builder id(ProductOptionGroupId id) {
            this.id = id;
            return this;
        }

        public Builder optionGroupId(OptionGroupId optionGroupId) {
            this.optionGroupId = optionGroupId;
            return this;
        }

        public Builder stepOrder(int stepOrder) {
            this.stepOrder = stepOrder;
            return this;
        }

        public Builder isRequired(boolean isRequired) {
            this.isRequired = isRequired;
            return this;
        }

        public Builder optionValues(List<ProductOptionValue> optionValues) {
            this.optionValues = optionValues;
            return this;
        }

        public Builder status(OptionStatus status) {
            this.status = status;
            return this;
        }

        public Builder isActive(boolean isActive) {
            this.status = isActive ? OptionStatus.ACTIVE : OptionStatus.INACTIVE;
            return this;
        }

        public ProductOptionGroup build() {
            validate();
            return new ProductOptionGroup(this);
        }

        private void validate() {
            if (optionGroupId == null) {
                throw new ProductDomainException("Option group ID cannot be null.");
            }
            validateStepOrder(this.stepOrder);
        }
    }
}
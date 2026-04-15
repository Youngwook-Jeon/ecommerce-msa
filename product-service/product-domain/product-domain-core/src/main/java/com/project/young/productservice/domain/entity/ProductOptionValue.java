package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.BaseEntity;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.common.domain.valueobject.ProductOptionValueId;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.valueobject.OptionStatus;

import lombok.Getter;

@Getter
public class ProductOptionValue extends BaseEntity<ProductOptionValueId> {

    // 글로벌 옵션 값에 대한 식별자 참조 (예: 'RED'의 글로벌 ID). 생성 후 변경 불가.
    private final OptionValueId optionValueId;

    // 이 상품에서 해당 옵션을 선택할 때 붙는 추가 금액 (기본 0)
    private Money priceDelta;

    // 프론트엔드에서 기본으로 선택되어 있을지 여부
    private boolean isDefault;

    // 이 상품에서의 로컬 상태 (글로벌 상태와 별개로 관리)
    private OptionStatus status;

    public static Builder builder() {
        return new Builder();
    }

    private ProductOptionValue(Builder builder) {
        super.setId(builder.id);
        this.optionValueId = builder.optionValueId;
        this.priceDelta = builder.priceDelta != null ? builder.priceDelta : Money.ZERO;
        this.isDefault = builder.isDefault;
        this.status = builder.status != null ? builder.status : OptionStatus.ACTIVE;
    }

    private ProductOptionValue(ProductOptionValueId id, OptionValueId optionValueId, Money priceDelta, boolean isDefault, OptionStatus status) {
        super.setId(id);
        this.optionValueId = optionValueId;
        this.priceDelta = priceDelta;
        this.isDefault = isDefault;
        this.status = status != null ? status : OptionStatus.ACTIVE;
    }

    // ========================================================================
    // 비즈니스 로직
    // ========================================================================

    void changePriceDelta(Money newPriceDelta) {
        validatePriceDelta(newPriceDelta);
        this.priceDelta = newPriceDelta;
    }

    void changeDefaultStatus(boolean isDefault) {
        this.isDefault = isDefault;
    }

    void changeStatus(OptionStatus newStatus) {
        if (newStatus == null) {
            throw new ProductDomainException("Option status cannot be null.");
        }
        if (!this.status.canTransitionTo(newStatus)) {
            throw new ProductDomainException("Invalid option status transition: " + this.status + " -> " + newStatus);
        }
        this.status = newStatus;
    }

    void deactivateLocalOption() {
        if (this.status == OptionStatus.DELETED) {
            return;
        }
        this.status = OptionStatus.INACTIVE;
    }

    void activateLocalOption() {
        if (this.status == OptionStatus.DELETED) {
            throw new ProductDomainException("Cannot activate a deleted option value.");
        }
        this.status = OptionStatus.ACTIVE;
    }

    public boolean isActive() {
        return this.status != null && this.status.isActive();
    }

    // ========================================================================
    // 검증 로직
    // ========================================================================

    private static void validatePriceDelta(Money priceDelta) {
        if (priceDelta == null) {
            throw new ProductDomainException("Price delta cannot be null.");
        }
    }

    // ========================================================================
    // FOR PERSISTENCE MAPPING ONLY
    // ========================================================================
    public static ProductOptionValue reconstitute(ProductOptionValueId id, OptionValueId optionValueId, Money priceDelta, boolean isDefault, OptionStatus status) {
        return new ProductOptionValue(id, optionValueId, priceDelta, isDefault, status);
    }

    public static ProductOptionValue reconstitute(ProductOptionValueId id, OptionValueId optionValueId, Money priceDelta, boolean isDefault, boolean isActive) {
        return new ProductOptionValue(
                id,
                optionValueId,
                priceDelta,
                isDefault,
                isActive ? OptionStatus.ACTIVE : OptionStatus.INACTIVE
        );
    }

    // ========================================================================
    // Builder
    // ========================================================================
    public static class Builder {
        private ProductOptionValueId id;
        private OptionValueId optionValueId;
        private Money priceDelta = Money.ZERO;
        private boolean isDefault = false;
        private OptionStatus status = OptionStatus.ACTIVE;

        public Builder id(ProductOptionValueId id) {
            this.id = id;
            return this;
        }

        public Builder optionValueId(OptionValueId optionValueId) {
            this.optionValueId = optionValueId;
            return this;
        }

        public Builder priceDelta(Money priceDelta) {
            this.priceDelta = priceDelta;
            return this;
        }

        public Builder isDefault(boolean isDefault) {
            this.isDefault = isDefault;
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

        public ProductOptionValue build() {
            validate();
            return new ProductOptionValue(this);
        }

        private void validate() {
            if (optionValueId == null) {
                throw new ProductDomainException("Option value ID cannot be null.");
            }
            validatePriceDelta(this.priceDelta);
        }
    }
}
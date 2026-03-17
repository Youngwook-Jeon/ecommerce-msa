package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.BaseEntity;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.common.domain.valueobject.ProductOptionValueId;
import com.project.young.productservice.domain.exception.ProductDomainException;

import lombok.Getter;

@Getter
public class ProductOptionValue extends BaseEntity<ProductOptionValueId> {

    // 글로벌 옵션 값에 대한 식별자 참조 (예: 'RED'의 글로벌 ID). 생성 후 변경 불가.
    private final OptionValueId optionValueId;

    // 이 상품에서 해당 옵션을 선택할 때 붙는 추가 금액 (기본 0)
    private Money priceDelta;

    // 프론트엔드에서 기본으로 선택되어 있을지 여부
    private boolean isDefault;

    // 이 상품에서 현재 해당 옵션을 판매(노출) 중인지 여부 (로컬 소프트 삭제/비활성화)
    private boolean isActive;

    public static Builder builder() {
        return new Builder();
    }

    private ProductOptionValue(Builder builder) {
        super.setId(builder.id);
        this.optionValueId = builder.optionValueId;
        this.priceDelta = builder.priceDelta != null ? builder.priceDelta : Money.ZERO;
        this.isDefault = builder.isDefault;
        this.isActive = builder.isActive;
    }

    private ProductOptionValue(ProductOptionValueId id, OptionValueId optionValueId, Money priceDelta, boolean isDefault, boolean isActive) {
        super.setId(id);
        this.optionValueId = optionValueId;
        this.priceDelta = priceDelta;
        this.isDefault = isDefault;
        this.isActive = isActive;
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

    void deactivateLocalOption() {
        this.isActive = false;
    }

    void activateLocalOption() {
        this.isActive = true;
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
    public static ProductOptionValue reconstitute(ProductOptionValueId id, OptionValueId optionValueId, Money priceDelta, boolean isDefault, boolean isActive) {
        return new ProductOptionValue(id, optionValueId, priceDelta, isDefault, isActive);
    }

    // ========================================================================
    // Builder
    // ========================================================================
    public static class Builder {
        private ProductOptionValueId id;
        private OptionValueId optionValueId;
        private Money priceDelta = Money.ZERO;
        private boolean isDefault = false;
        private boolean isActive = true;

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

        public Builder isActive(boolean isActive) {
            this.isActive = isActive;
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
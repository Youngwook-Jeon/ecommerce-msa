package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductOptionGroupId;
import com.project.young.common.domain.valueobject.ProductOptionValueId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import lombok.Getter;

import java.util.*;

@Getter
public class Product extends AggregateRoot<ProductId> {

    private CategoryId categoryId;
    private String name;
    private String description;
    private Money basePrice;
    private ProductStatus status;
    private final ConditionType conditionType;
    private String brand;
    private String mainImageUrl;

    private final List<ProductOptionGroup> optionGroups;
    private final List<ProductVariant> variants;

    public static Builder builder() {
        return new Builder();
    }

    private Product(Builder builder) {
        super.setId(builder.productId);
        this.categoryId = builder.categoryId;
        this.name = builder.name;
        this.description = builder.description;
        this.basePrice = builder.basePrice;
        this.status = builder.status;
        this.conditionType = builder.conditionType;
        this.brand = builder.brand;
        this.mainImageUrl = builder.mainImageUrl;

        this.optionGroups = builder.optionGroups != null ? builder.optionGroups : new ArrayList<>();
        this.variants = builder.variants != null ? builder.variants : new ArrayList<>();
    }

    private Product(ProductId productId, CategoryId categoryId, String name, String description,
                    Money basePrice, ProductStatus status, ConditionType conditionType, String brand,  String mainImageUrl,
                    List<ProductOptionGroup> optionGroups, List<ProductVariant> variants) {
        super.setId(productId);
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.status = status;
        this.conditionType = conditionType;
        this.brand = brand;
        this.mainImageUrl = mainImageUrl;

        this.optionGroups = optionGroups != null ? new ArrayList<>(optionGroups) : new ArrayList<>();
        this.variants = variants != null ? new ArrayList<>(variants) : new ArrayList<>();
    }

    public Optional<CategoryId> getCategoryId() {
        return Optional.ofNullable(categoryId);
    }

    public List<ProductOptionGroup> getOptionGroups() {
        if (this.optionGroups == null || this.optionGroups.isEmpty()) {
            return List.of();
        }
        return List.copyOf(this.optionGroups);
    }

    public List<ProductVariant> getVariants() {
        if (this.variants == null || this.variants.isEmpty()) {
            return List.of();
        }
        return List.copyOf(this.variants);
    }

    /**
     * @param categoryId new category id (nullable)
     */
    public void changeCategoryId(CategoryId categoryId) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change the category of a deleted product.");
        }
        this.categoryId = categoryId;
    }

    public void changeName(String newName) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change the name of a deleted product.");
        }
        validateName(newName);
        this.name = newName;
    }

    public void changeDescription(String newDescription) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change the description of a deleted product.");
        }
        validateDescription(newDescription);
        this.description = newDescription;
    }

    public void changeBasePrice(Money newBasePrice) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change the base price of a deleted product.");
        }
        validateBasePrice(newBasePrice);
        this.basePrice = newBasePrice;

        // 기본 가격이 변경되면 하위 Variant 들의 계산된 가격도 함께 갱신되어야 함
        recalculateVariantPrices();
    }

    public void changeStatus(ProductStatus newStatus) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change the status of a deleted product.");
        }
        if (!this.status.canTransitionTo(newStatus)) {
            throw new ProductDomainException("Invalid status provided for update: " + newStatus);
        }
        this.status = newStatus;
    }

    public void changeBrand(String newBrand) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change the brand of a deleted product.");
        }
        validateBrand(newBrand);
        this.brand = newBrand;
    }

    public void changeMainImageUrl(String newUrl) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change the main image of a deleted product.");
        }
        validateMainImageUrl(newUrl);
        this.mainImageUrl = newUrl;
    }

    public boolean isDeleted() {
        return this.status.isDeleted();
    }

    public void markAsDeleted() {
        if (isDeleted()) {
            return;
        }
        this.status = ProductStatus.DELETED;
        // 제품이 삭제되면 하위 변형들도 논리적 삭제 처리
        // 타 도메인에서 단독 조회가 발생할 수 있는 변형에만 명시적으로 삭제 전파
        if (this.variants != null) {
            this.variants.forEach(ProductVariant::markAsDeleted);
        }
    }

    // ========================================================================
    // Aggregate Root 비즈니스 로직 (옵션 & 변형 관리)
    // ========================================================================

    public void addOptionGroup(ProductOptionGroup group) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot add option group to a deleted product.");
        }
        boolean exists = this.optionGroups.stream()
                .anyMatch(g -> g.getOptionGroupId().equals(group.getOptionGroupId()));
        if (exists) {
            throw new ProductDomainException("Option group already exists in this product.");
        }
        this.optionGroups.add(group);
    }

    public void addProductOptionValue(ProductOptionGroupId productOptionGroupId, ProductOptionValue newValue) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot add option value to a deleted product.");
        }
        if (productOptionGroupId == null || newValue == null) {
            throw new ProductDomainException("Product option group id and option value must not be null.");
        }
        ProductOptionGroup group = this.optionGroups.stream()
                .filter(g -> g.getId().equals(productOptionGroupId))
                .findFirst()
                .orElseThrow(() -> new ProductDomainException("Product option group not found in this product."));
        group.addOptionValue(newValue);
    }

    public void addVariant(ProductVariant variant) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot add variant to a deleted product.");
        }
        boolean skuExists = this.variants.stream()
                .anyMatch(v -> v.getSku().equals(variant.getSku()));
        if (skuExists) {
            throw new ProductDomainException("Variant with SKU " + variant.getSku() + " already exists.");
        }

        // 데이터 정합성 검증: 선택된 옵션이 이 상품에 존재하는 옵션인지 확인
        validateVariantOptions(variant.getSelectedOptionValues());

        // 초기 가격 세팅
        Money totalDelta = calculateTotalOptionPriceDelta(variant.getSelectedOptionValues());
        variant.updateCalculatedPrice(this.basePrice.add(totalDelta));

        this.variants.add(variant);
    }

    public void changeOptionValuePriceDelta(ProductOptionValueId targetValueId, Money newPriceDelta) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change option price on a deleted product.");
        }

        // 1. 대상 옵션 찾아서 가격 변경
        boolean optionFound = false;
        for (ProductOptionGroup group : this.optionGroups) {
            for (ProductOptionValue value : group.getOptionValues()) {
                if (value.getId().equals(targetValueId)) {
                    value.changePriceDelta(newPriceDelta);
                    optionFound = true;
                    break;
                }
            }
        }

        if (!optionFound) {
            throw new ProductDomainException("Option value not found in this product.");
        }

        // 2. 가격이 변경되었으니 전체 Variant 가격 재계산
        recalculateVariantPrices();
    }

    public ProductVariant updateVariantDetails(ProductVariantId variantId, Integer newStockQuantity, ProductStatus newStatus) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot update variant in a deleted product.");
        }
        ProductVariant target = this.variants.stream()
                .filter(v -> v.getId().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new ProductDomainException("Variant not found in this product."));

        if (newStockQuantity != null && target.getStockQuantity() != newStockQuantity) {
            target.setStockQuantity(newStockQuantity);
        }
        if (newStatus != null && target.getStatus() != newStatus) {
            target.changeStatus(newStatus);
        }
        return target;
    }

    public ProductVariant deleteVariant(ProductVariantId variantId) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot delete variant in a deleted product.");
        }
        ProductVariant target = this.variants.stream()
                .filter(v -> v.getId().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new ProductDomainException("Variant not found in this product."));
        target.markAsDeleted();
        return target;
    }

    public ProductOptionValue deactivateProductOptionValue(ProductOptionValueId productOptionValueId) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot deactivate option value in a deleted product.");
        }
        for (ProductOptionGroup group : this.optionGroups) {
            for (ProductOptionValue value : group.getOptionValues()) {
                if (value.getId().equals(productOptionValueId)) {
                    value.deactivateLocalOption();
                    return value;
                }
            }
        }
        throw new ProductDomainException("Product option value not found in this product.");
    }

    private void validateVariantOptions(Set<ProductOptionValueId> selectedOptions) {
        List<ProductOptionValueId> validOptionIds = this.optionGroups.stream()
                .filter(group -> group.getStatus() == null || !group.getStatus().isDeleted())
                .flatMap(group -> group.getOptionValues().stream())
                .filter(ProductOptionValue::isActive)
                .map(ProductOptionValue::getId)
                .toList();

        for (ProductOptionValueId selectedId : selectedOptions) {
            if (!validOptionIds.contains(selectedId)) {
                throw new ProductDomainException("Invalid or inactive option value ID for this product: " + selectedId.getValue());
            }
        }

        // 모든 필수(Required) 옵션 그룹에서 최소(혹은 정확히) 1개의 옵션이 선택되었는지 확인
        // 로컬 상태가 DELETED인 그룹은 더 이상 변형 선택 대상이 아니므로 제외한다.
        for (ProductOptionGroup group : this.optionGroups) {
            if (group.getStatus() != null && group.getStatus().isDeleted()) {
                continue;
            }
            if (group.isRequired()) {
                boolean hasSelectedOptionInGroup = group.getOptionValues().stream()
                        .filter(ProductOptionValue::isActive)
                        .anyMatch(val -> selectedOptions.contains(val.getId()));

                if (!hasSelectedOptionInGroup) {
                    throw new ProductDomainException("Missing required option from group: " + group.getOptionGroupId().getValue());
                }
            }
        }
    }

    private void recalculateVariantPrices() {
        for (ProductVariant variant : this.variants) {
            Money totalDelta = calculateTotalOptionPriceDelta(variant.getSelectedOptionValues());
            variant.updateCalculatedPrice(this.basePrice.add(totalDelta));
        }
    }

    private Money calculateTotalOptionPriceDelta(Set<ProductOptionValueId> selectedOptionValueIds) {
        Money totalDelta = Money.ZERO;
        for (ProductOptionGroup group : this.optionGroups) {
            for (ProductOptionValue optionValue : group.getOptionValues()) {
                if (selectedOptionValueIds.contains(optionValue.getId())) {
                    totalDelta = totalDelta.add(optionValue.getPriceDelta());
                }
            }
        }
        return totalDelta;
    }

    // ========================================================================
    // 검증 로직 (Validation)
    // ========================================================================
    private static void validateName(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new ProductDomainException("Product name cannot be null or blank.");
        }
        if (newName.length() < 2 || newName.length() > 100) {
            throw new ProductDomainException("Product name must be between 2 and 100 characters.");
        }
    }

    private static void validateDescription(String newDescription) {
        if (newDescription == null || newDescription.isBlank()) {
            throw new ProductDomainException("Product description cannot be null or blank.");
        }
        if (newDescription.length() < 20) {
            throw new ProductDomainException("Product description must be at least 20 characters.");
        }
    }

    private static void validateBasePrice(Money newBasePrice) {
        if (newBasePrice == null) {
            throw new ProductDomainException("Product base price cannot be null.");
        }
        if (newBasePrice.isLessThanOrEqualZero()) {
            throw new ProductDomainException("Product base price must be greater than zero.");
        }
    }

    private static void validateBrand(String newBrand) {
        if (newBrand == null || newBrand.isBlank()) {
            throw new ProductDomainException("Product brand cannot be null or blank.");
        }
        if (newBrand.length() < 2 || newBrand.length() > 100) {
            throw new ProductDomainException("Product brand must be between 2 and 100 characters.");
        }
    }

    private static void validateMainImageUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new ProductDomainException("Main image URL cannot be null or blank.");
        }
        if (url.length() > 500) {
            throw new ProductDomainException("Main image URL is too long.");
        }
    }

    /**
     * !!! FOR PERSISTENCE MAPPING ONLY !!!
     * Reconstitutes a Product object from a persistent state (e.g., database).
     * This method bypasses initial creation validations and should NOT be used
     * for creating new business objects. Use the builder for new instances.
     */
    public static Product reconstitute(ProductId productId, CategoryId categoryId, String name, String description,
                                      Money basePrice, ProductStatus status, ConditionType conditionType, String brand, String mainImageUrl,
                                       List<ProductOptionGroup> optionGroups, List<ProductVariant> variants) {
        return new Product(productId, categoryId, name, description, basePrice, status, conditionType, brand, mainImageUrl, optionGroups, variants);
    }

    public static class Builder {
        private ProductId productId;
        private CategoryId categoryId;
        private String name;
        private String description;
        private Money basePrice;
        private ProductStatus status = ProductStatus.DRAFT;
        private ConditionType conditionType;
        private String brand;
        private String mainImageUrl;
        private List<ProductOptionGroup> optionGroups = new ArrayList<>();
        private List<ProductVariant> variants = new ArrayList<>();

        public Builder productId(ProductId productId) {
            this.productId = productId;
            return this;
        }

        public Builder categoryId(CategoryId categoryId) {
            this.categoryId = categoryId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder basePrice(Money basePrice) {
            this.basePrice = basePrice;
            return this;
        }

        public Builder status(ProductStatus status) {
            this.status = status;
            return this;
        }

        public Builder conditionType(ConditionType conditionType) {
            this.conditionType = conditionType;
            return this;
        }

        public Builder brand(String brand) {
            this.brand = brand;
            return this;
        }

        public Builder mainImageUrl(String mainImageUrl) {
            this.mainImageUrl = mainImageUrl;
            return this;
        }

        public Builder optionGroups(List<ProductOptionGroup> optionGroups) {
            this.optionGroups = optionGroups;
            return this;
        }

        public Builder variants(List<ProductVariant> variants) {
            this.variants = variants;
            return this;
        }

        public Product build() {
            validate();
            return new Product(this);
        }

        private void validate() {
            validateName(this.name);
            validateDescription(this.description);
            validateBasePrice(this.basePrice);
            validateBrand(this.brand);
            validateMainImageUrl(this.mainImageUrl);
            if (status == null) {
                throw new ProductDomainException("Product status cannot be null.");
            }
            if (status.isDeleted()) {
                throw new ProductDomainException("Product must not be created with DELETED status.");
            }
            if (conditionType == null) {
                throw new ProductDomainException("Product condition type cannot be null.");
            }
        }
    }
}


package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.domain.exception.ProductDomainException;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Getter
public class Product extends AggregateRoot<ProductId> {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    public static final String CONDITION_NEW = "new";
    public static final String CONDITION_USED = "used";
    public static final String CONDITION_REFURBISHED = "refurbished";

    private CategoryId categoryId;
    private String name;
    private String description;
    private BigDecimal basePrice;
    private String status;
    private String conditionType;
    private BrandId brandId;
    private Instant createdAt;
    private Instant updatedAt;

    private Product(Builder builder) {
        super.setId(builder.productId);
        this.categoryId = builder.categoryId;
        this.name = builder.name;
        this.description = builder.description;
        this.basePrice = builder.basePrice;
        this.status = builder.status;
        this.conditionType = builder.conditionType;
        this.brandId = builder.brandId;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    private Product(ProductId productId, CategoryId categoryId, String name, String description,
                    BigDecimal basePrice, String status, String conditionType, BrandId brandId,
                    Instant createdAt, Instant updatedAt) {
        super.setId(productId);
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.status = status;
        this.conditionType = conditionType;
        this.brandId = brandId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void changeName(String name) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change the name of a deleted product.");
        }
        validateProductName(name);
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public void changeDescription(String description) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change the description of a deleted product.");
        }
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void changeBasePrice(BigDecimal basePrice) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change the base price of a deleted product.");
        }
        validateBasePrice(basePrice);
        this.basePrice = basePrice;
        this.updatedAt = Instant.now();
    }

    public void changeStatus(String status) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change the status of a deleted product.");
        }
        if (STATUS_DELETED.equals(status)) {
            throw new ProductDomainException("Cannot change the status as deleted. Use markAsDeleted() instead.");
        }
        if (!isValidStatus(status)) {
            throw new ProductDomainException("Invalid status: " + status);
        }
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void changeConditionType(String conditionType) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change the condition type of a deleted product.");
        }
        if (!isValidConditionType(conditionType)) {
            throw new ProductDomainException("Invalid condition type: " + conditionType);
        }
        this.conditionType = conditionType;
        this.updatedAt = Instant.now();
    }

    public void changeCategory(CategoryId categoryId) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change the category of a deleted product.");
        }
        this.categoryId = categoryId;
        this.updatedAt = Instant.now();
    }

    public void changeBrand(BrandId brandId) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change the brand of a deleted product.");
        }
        this.brandId = brandId;
        this.updatedAt = Instant.now();
    }

    public void markAsDeleted() {
        if (isDeleted()) {
            return; // Idempotent
        }
        this.status = STATUS_DELETED;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(this.status);
    }

    public boolean isDeleted() {
        return STATUS_DELETED.equals(this.status);
    }

    public Optional<CategoryId> getCategoryId() {
        return Optional.ofNullable(categoryId);
    }

    public Optional<BrandId> getBrandId() {
        return Optional.ofNullable(brandId);
    }

    private static boolean isValidStatus(String status) {
        return STATUS_ACTIVE.equals(status) || STATUS_INACTIVE.equals(status);
    }

    private static boolean isValidConditionType(String conditionType) {
        return CONDITION_NEW.equals(conditionType) ||
                CONDITION_USED.equals(conditionType) ||
                CONDITION_REFURBISHED.equals(conditionType);
    }

    private static void validateProductName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ProductDomainException("Product name cannot be null or empty.");
        }
        if (name.length() > 255) {
            throw new ProductDomainException("Product name must be between 1 and 255 characters.");
        }
    }

    private static void validateBasePrice(BigDecimal basePrice) {
        if (basePrice == null) {
            throw new ProductDomainException("Base price cannot be null.");
        }

        if (basePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ProductDomainException("Base price cannot be negative.");
        }

        if (basePrice.compareTo(new BigDecimal("999999999.99")) > 0) {
            throw new ProductDomainException("Base price is too large.");
        }
    }

    /**
     * !!! FOR PERSISTENCE MAPPING ONLY !!!
     * Reconstitutes a Product object from a persistent state (e.g., database).
     * This method bypasses initial creation validations and should NOT be used
     * for creating new business objects. Use the builder for new instances.
     */
    public static Product reconstitute(ProductId productId, CategoryId categoryId, String name,
                                       String description, BigDecimal basePrice, String status,
                                       String conditionType, BrandId brandId, Instant createdAt,
                                       Instant updatedAt) {
        return new Product(productId, categoryId, name, description, basePrice, status,
                conditionType, brandId, createdAt, updatedAt);
    }

    public static final class Builder {
        private ProductId productId;
        private CategoryId categoryId;
        private String name;
        private String description;
        private BigDecimal basePrice;
        private String status = STATUS_ACTIVE;
        private String conditionType = CONDITION_NEW;
        private BrandId brandId;
        private Instant createdAt;
        private Instant updatedAt;

        private Builder() {}

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

        public Builder basePrice(BigDecimal basePrice) {
            this.basePrice = basePrice;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder conditionType(String conditionType) {
            this.conditionType = conditionType;
            return this;
        }

        public Builder brandId(BrandId brandId) {
            this.brandId = brandId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Product build() {
            validate();
            setDefaults();
            return new Product(this);
        }

        private void validate() {
            // 필수 필드 검증
            Product.validateProductName(name);

            Product.validateBasePrice(basePrice);

            // status 검증
            if (status != null && !Product.isValidStatus(status)) {
                throw new ProductDomainException("Product must have a valid initial status (ACTIVE, INACTIVE).");
            }

            // conditionType 검증
            if (conditionType != null && !Product.isValidConditionType(conditionType)) {
                throw new ProductDomainException("Product must have a valid condition type.");
            }
        }

        private void setDefaults() {
            if (productId == null) {
                productId = new ProductId();
            }

            if (status == null) {
                status = STATUS_ACTIVE;
            }

            if (conditionType == null) {
                conditionType = CONDITION_NEW;
            }

            Instant now = Instant.now();
            if (createdAt == null) {
                createdAt = now;
            }

            if (updatedAt == null) {
                updatedAt = now;
            }
        }
    }
}
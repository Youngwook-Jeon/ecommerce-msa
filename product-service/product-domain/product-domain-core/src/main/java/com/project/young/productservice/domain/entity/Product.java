package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import lombok.Getter;

import java.util.Optional;

@Getter
public class Product extends AggregateRoot<ProductId> {

    private CategoryId categoryId;
    private String name;
    private String description;
    private Money basePrice;
    private ProductStatus status;
    private final ConditionType conditionType;
    private String brand;

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
    }

    private Product(ProductId productId, CategoryId categoryId, String name, String description,
                    Money basePrice, ProductStatus status, ConditionType conditionType, String brand) {
        super.setId(productId);
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.status = status;
        this.conditionType = conditionType;
        this.brand = brand;
    }

    public Optional<CategoryId> getCategoryId() {
        return Optional.ofNullable(categoryId);
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

    public boolean isDeleted() {
        return this.status.isDeleted();
    }

    public void markAsDeleted() {
        if (isDeleted()) {
            return;
        }
        this.status = ProductStatus.DELETED;
    }

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

    /**
     * !!! FOR PERSISTENCE MAPPING ONLY !!!
     * Reconstitutes a Product object from a persistent state (e.g., database).
     * This method bypasses initial creation validations and should NOT be used
     * for creating new business objects. Use the builder for new instances.
     *
     * @param productId    The existing product ID from the database.
     * @param categoryId   The existing category ID from the database (may be null).
     * @param name         The existing name from the database.
     * @param description  The existing description from the database.
     * @param basePrice    The existing base price from the database.
     * @param status       The existing status from the database.
     * @param conditionType The existing condition type from the database.
     * @param brand        The existing brand from the database.
     * @return A reconstituted Product object.
     */
    public static Product reconstitute(ProductId productId, CategoryId categoryId, String name, String description,
                                      Money basePrice, ProductStatus status, ConditionType conditionType, String brand) {
        return new Product(productId, categoryId, name, description, basePrice, status, conditionType, brand);
    }

    public static class Builder {
        private ProductId productId;
        private CategoryId categoryId;
        private String name;
        private String description;
        private Money basePrice;
        private ProductStatus status = ProductStatus.ACTIVE;
        private ConditionType conditionType;
        private String brand;

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

        public Product build() {
            validate();
            return new Product(this);
        }

        private void validate() {
            validateName(this.name);
            validateDescription(this.description);
            validateBasePrice(this.basePrice);
            validateBrand(this.brand);
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

package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.BaseEntity;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductOptionValueId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

@Getter
public class ProductVariant extends BaseEntity<ProductVariantId> {
    private final String sku;
    private int stockQuantity;
    private ProductStatus status;
    /**
     * Denormalized final price (calculated and stored).
     * It is set/updated by Product aggregate when base price or option deltas change.
     */
    private Money calculatedPrice;
    private final Set<ProductOptionValueId> selectedOptionValues;

    public static Builder builder() {
        return new Builder();
    }

    private ProductVariant(Builder builder) {
        super.setId(builder.id);
        this.sku = builder.sku;
        this.stockQuantity = builder.stockQuantity;
        this.status = builder.status;
        this.calculatedPrice = builder.calculatedPrice != null ? builder.calculatedPrice : Money.ZERO;
        this.selectedOptionValues = builder.selectedOptionValues != null
                ? new HashSet<>(builder.selectedOptionValues)
                : new HashSet<>();
    }

    private ProductVariant(ProductVariantId id,
                           String sku,
                           int stockQuantity,
                           ProductStatus status,
                           Money calculatedPrice,
                           Set<ProductOptionValueId> selectedOptionValues) {
        super.setId(id);
        this.sku = sku;
        this.stockQuantity = stockQuantity;
        this.status = status;
        this.calculatedPrice = calculatedPrice != null ? calculatedPrice : Money.ZERO;
        this.selectedOptionValues = selectedOptionValues != null ? new HashSet<>(selectedOptionValues) : new HashSet<>();
    }

    public Set<ProductOptionValueId> getSelectedOptionValues() {
        if (this.selectedOptionValues == null || this.selectedOptionValues.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(this.selectedOptionValues);
    }

    /**
     * !!! FOR PERSISTENCE MAPPING ONLY !!!
     * Reconstitutes a ProductVariant object from a persistent state (e.g., database).
     * This method bypasses initial creation validations and should NOT be used
     * for creating new business objects. Use the builder for new instances.
     */
    public static ProductVariant reconstitute(ProductVariantId id,
                                              String sku,
                                              int stockQuantity,
                                              ProductStatus status,
                                              Money calculatedPrice,
                                              Set<ProductOptionValueId> selectedOptionValues) {
        return new ProductVariant(id, sku, stockQuantity, status, calculatedPrice, selectedOptionValues);
    }

    public boolean isDeleted() {
        return this.status.isDeleted();
    }

    void markAsDeleted() {
        if (isDeleted()) {
            return;
        }
        this.status = ProductStatus.DELETED;
    }

    void updateCalculatedPrice(Money calculatedPrice) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot update calculated price to a deleted variant.");
        }
        validateCalculatedPrice(calculatedPrice);
        this.calculatedPrice = calculatedPrice;
    }

    void changeStatus(ProductStatus newStatus) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change the status of a deleted variant.");
        }
        if (!this.status.canTransitionTo(newStatus)) {
            throw new ProductDomainException("Invalid status provided for update: " + newStatus);
        }
        this.status = newStatus;
    }

    void setStockQuantity(int newStockQuantity) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change stock of a deleted variant.");
        }
        validateStockQuantity(newStockQuantity);
        this.stockQuantity = newStockQuantity;
        if (this.stockQuantity == 0) {
            this.status = ProductStatus.OUT_OF_STOCK;
        } else if (this.status == ProductStatus.OUT_OF_STOCK) {
            this.status = ProductStatus.ACTIVE;
        }
    }

    void increaseStock(int amount) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change stock of a deleted variant.");
        }
        if (amount <= 0) {
            throw new ProductDomainException("Increase amount must be greater than zero.");
        }
        this.stockQuantity += amount;

        if (this.stockQuantity > 0 && this.status == ProductStatus.OUT_OF_STOCK) {
            this.status = ProductStatus.ACTIVE;
        }
    }

    void decreaseStock(int amount) {
        if (isDeleted()) {
            throw new ProductDomainException("Cannot change stock of a deleted variant.");
        }
        if (amount <= 0) {
            throw new ProductDomainException("Decrease amount must be greater than zero.");
        }
        int next = this.stockQuantity - amount;
        if (next < 0) {
            throw new ProductDomainException("Stock quantity cannot be negative.");
        }
        this.stockQuantity = next;

        if (this.stockQuantity == 0 && this.status == ProductStatus.ACTIVE) {
            this.status = ProductStatus.OUT_OF_STOCK;
        }
    }

    private static void validateSku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new ProductDomainException("SKU cannot be null or blank.");
        }
        if (sku.length() > 100) {
            throw new ProductDomainException("SKU must be 100 characters or less.");
        }
    }

    private static void validateStockQuantity(int stockQuantity) {
        if (stockQuantity < 0) {
            throw new ProductDomainException("Stock quantity cannot be negative.");
        }
    }

    private static void validateCalculatedPrice(Money calculatedPrice) {
        if (calculatedPrice == null) {
            throw new ProductDomainException("Calculated price cannot be null.");
        }
        if (calculatedPrice.isLessThan(Money.ZERO)) {
            throw new ProductDomainException("Calculated price must be greater than or equal to zero.");
        }
    }

    public static class Builder {
        private ProductVariantId id;
        private String sku;
        private int stockQuantity = 0;
        private ProductStatus status = ProductStatus.ACTIVE;
        private Money calculatedPrice = Money.ZERO;
        private Set<ProductOptionValueId> selectedOptionValues = new HashSet<>();

        public Builder id(ProductVariantId id) {
            this.id = id;
            return this;
        }

        public Builder sku(String sku) {
            this.sku = sku;
            return this;
        }

        public Builder stockQuantity(int stockQuantity) {
            this.stockQuantity = stockQuantity;
            return this;
        }

        public Builder status(ProductStatus status) {
            this.status = status;
            return this;
        }

        public Builder calculatedPrice(Money calculatedPrice) {
            this.calculatedPrice = calculatedPrice;
            return this;
        }

        public Builder selectedOptionValues(Set<ProductOptionValueId> selectedOptionValues) {
            this.selectedOptionValues = selectedOptionValues;
            return this;
        }

        public ProductVariant build() {
            validate();
            return new ProductVariant(this);
        }

        private void validate() {
            validateSku(this.sku);
            validateStockQuantity(this.stockQuantity);
            validateCalculatedPrice(calculatedPrice);

            if (status == null) {
                throw new ProductDomainException("Product variant status cannot be null.");
            }
            if (status.isDeleted()) {
                throw new ProductDomainException("Product variant must not be created with DELETED status.");
            }
        }
    }

}
package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;

public class Product extends AggregateRoot<ProductId> {
    private final String productName;
    private final String description;
    private final Money price;

    public static Builder builder() {
        return new Builder();
    }

    public String getProductName() {
        return productName;
    }

    public String getDescription() {
        return description;
    }

    public Money getPrice() {
        return price;
    }

    private Product(Builder builder) {
        super.setId(builder.productId);
        this.productName = builder.productName;
        this.description = builder.description;
        this.price = builder.price;
    }

    public static final class Builder {
        private ProductId productId;
        private String productName;
        private String description;
        private Money price;

        private Builder() {}

        public Builder productId(ProductId val) {
            productId = val;
            return this;
        }

        public Builder productName(String val) {
            productName = val;
            return this;
        }

        public Builder description(String val) {
            description = val;
            return this;
        }

        public Builder price(Money val) {
            price = val;
            return this;
        }

        public Product build() {
            return new Product(this);
        }
    }
}

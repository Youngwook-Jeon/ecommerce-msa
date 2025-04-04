package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.domain.exception.CategoryDomainException;

import java.util.Optional;

public class Category extends AggregateRoot<CategoryId> {

    private final String name;
    private final CategoryId parentId;

    public static Builder builder() {
        return new Builder();
    }

    private Category(Builder builder) {
        super.setId(builder.categoryId);
        this.name = builder.name;
        this.parentId = builder.parentId;
    }

    public String getName() {
        return name;
    }

    public Optional<CategoryId> getParentId() {
        return Optional.ofNullable(parentId);
    }

    public void assignId(Long val) {
        if (this.getId() != null) {
            throw new CategoryDomainException("This category id is already assigned.");
        }
        super.setId(new CategoryId(val));
    }

    public static class Builder {
        private CategoryId categoryId;
        private String name;
        private CategoryId parentId;

        public Builder categoryId(CategoryId categoryId) {
            this.categoryId = categoryId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder parentId(CategoryId parentId) {
            this.parentId = parentId;
            return this;
        }

        public Category build() {
            return new Category(this);
        }
    }
}

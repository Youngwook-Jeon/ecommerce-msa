package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.domain.exception.CategoryDomainException;

import java.util.Optional;

public class Category extends AggregateRoot<CategoryId> {

    private String name;
    private CategoryId parentId;
    private String status;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    public static Builder builder() {
        return new Builder();
    }

    private Category(Builder builder) {
        super.setId(builder.categoryId);
        this.name = builder.name;
        this.parentId = builder.parentId;
        this.status = builder.status;
    }

    public String getName() {
        return name;
    }

    public Optional<CategoryId> getParentId() {
        return Optional.ofNullable(parentId);
    }

    public String getStatus() {
        return status;
    }

    public void changeName(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new CategoryDomainException("New category name cannot be null or blank.");
        }
        if (newName.length() < 2 || newName.length() > 50) {
            throw new CategoryDomainException("New category name must be between 2 and 50 characters.");
        }
        this.name = newName;
    }

    public void changeParent(CategoryId newParentId) {
        if (this.getId() != null && newParentId != null && newParentId.equals(this.getId())) {
            throw new CategoryDomainException("A category cannot be set as its own parent.");
        }
        this.parentId = newParentId;
    }

    public void markAsDeleted() {
        if (STATUS_DELETED.equals(this.status)) return;
        this.status = STATUS_DELETED;
    }

    public void assignId(Long val) {
        if (val == null || val <= 0) {
            throw new CategoryDomainException("Category ID value must be positive.");
        }
        if (this.getId() != null) {
            throw new CategoryDomainException("This category id is already assigned.");
        }
        super.setId(new CategoryId(val));
    }

    public static class Builder {
        private CategoryId categoryId;
        private String name;
        private CategoryId parentId;
        private String status = STATUS_ACTIVE;

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

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Category build() {
            validate();
            return new Category(this);
        }

        private void validate() {
            if (name == null || name.isBlank()) {
                throw new CategoryDomainException("Category name cannot be blank.");
            }

            if (name.length() < 2 || name.length() > 50) {
                throw new CategoryDomainException("Category name must be between 2 and 50 characters.");
            }

            if (parentId != null && parentId.equals(categoryId)) {
                throw new CategoryDomainException("Category cannot be its own parent.");
            }

            if (this.status == null || (!this.status.equals(STATUS_ACTIVE) && !this.status.equals(STATUS_DELETED))) {
                throw new CategoryDomainException("Category must have a valid initial status (ACTIVE or DELETED).");
            }

        }
    }
}

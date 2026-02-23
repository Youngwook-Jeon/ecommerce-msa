package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import lombok.Getter;

import java.util.Optional;

@Getter
public class Category extends AggregateRoot<CategoryId> {

    private String name;
    private CategoryId parentId;
    private CategoryStatus status;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
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

    private Category(CategoryId id, String name, CategoryId parentId, CategoryStatus status) {
        super.setId(id);
        this.name = name;
        this.parentId = parentId;
        this.status = status;
    }

    public Optional<CategoryId> getParentId() {
        return Optional.ofNullable(parentId);
    }

    public void changeName(String newName) {
        if (isDeleted()) {
            throw new CategoryDomainException("Cannot change the name of a deleted category.");
        }
        if (newName == null || newName.isBlank()) {
            throw new CategoryDomainException("New category name cannot be null or blank.");
        }
        if (newName.length() < 2 || newName.length() > 50) {
            throw new CategoryDomainException("New category name must be between 2 and 50 characters.");
        }
        this.name = newName;
    }

    public void changeParent(CategoryId newParentId) {
        if (isDeleted()) {
            throw new CategoryDomainException("Cannot change the parent of a deleted category.");
        }
        if (this.getId() != null && newParentId != null && newParentId.equals(this.getId())) {
            throw new CategoryDomainException("A category cannot be set as its own parent.");
        }
        this.parentId = newParentId;
    }

    public void changeStatus(CategoryStatus newStatus) {
        if (this.status.isDeleted()) {
            throw new CategoryDomainException("Cannot change the status of a deleted category.");
        }
        if (!this.status.canTransitionTo(newStatus)) {
            throw new CategoryDomainException("Invalid status provided for update: " + newStatus);
        }
        this.status = newStatus;
    }

    public void markAsDeleted() {
        if (isDeleted()) {
            return; // Idempotent
        }
        this.status = CategoryStatus.DELETED;
    }

    public boolean isDeleted() {
        return this.status.isDeleted();
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
        private CategoryStatus status = CategoryStatus.ACTIVE;

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

        public Builder status(CategoryStatus status) {
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

            if (!this.status.isActive()) {
                throw new CategoryDomainException("Category must have a valid initial ACTIVE status.");
            }

        }
    }

    /**
     * !!! FOR PERSISTENCE MAPPING ONLY !!!
     * Reconstitutes a Category object from a persistent state (e.g., database).
     * This method bypasses initial creation validations and should NOT be used
     * for creating new business objects. Use the builder for new instances.
     * @param id The existing ID from the database.
     * @param name The existing name from the database.
     * @param parentId The existing parent ID from the database.
     * @param status The existing status from the database.
     * @return A reconstituted Category object.
     */
    public static Category reconstitute(CategoryId id, String name, CategoryId parentId, CategoryStatus status) {
        return new Category(id, name, parentId, status);
    }
}

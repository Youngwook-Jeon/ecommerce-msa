package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Objects;

@Component
public class CategoryDataAccessMapper {

    public Category categoryEntityToCategory(CategoryEntity categoryEntity) {
        Objects.requireNonNull(categoryEntity, "categoryEntity must not be null.");

        CategoryId parentId = toParentId(categoryEntity);

        // Use the 'reconstitute' factory method instead of the builder
        return Category.reconstitute(
                new CategoryId(categoryEntity.getId()),
                categoryEntity.getName(),
                parentId,
                toDomainStatus(categoryEntity.getStatus()));
    }

    public CategoryEntity categoryToCategoryEntity(Category category, CategoryEntity parentEntity) {
        Objects.requireNonNull(category, "category must not be null.");

        return CategoryEntity.builder()
                .id(category.getId() != null ? category.getId().getValue() : null)
                .name(category.getName())
                .status(toEntityStatus(category.getStatus()))
                .parent(parentEntity)
                .build();
    }

    public void updateEntityFromDomain(Category domainCategory, CategoryEntity categoryEntity) {
        Objects.requireNonNull(domainCategory, "domainCategory must not be null.");
        Objects.requireNonNull(categoryEntity, "categoryEntity must not be null.");

        // Parent relationship change is handled in the repository layer.
        categoryEntity.setName(domainCategory.getName());
        categoryEntity.setStatus(toEntityStatus(domainCategory.getStatus()));
    }

    /**
     * Domain Enum -> Entity Enum
     */
    public CategoryStatusEntity toEntityStatus(CategoryStatus domainStatus) {
        Objects.requireNonNull(domainStatus, "domainStatus must not be null.");

        return CategoryStatusEntity.valueOf(domainStatus.name());
    }

    /**
     * Entity Enum -> Domain Enum
     */
    public CategoryStatus toDomainStatus(CategoryStatusEntity entityStatus) {
        Objects.requireNonNull(entityStatus, "entityStatus must not be null.");

        return CategoryStatus.valueOf(entityStatus.name());
    }

    private CategoryId toParentId(CategoryEntity categoryEntity) {
        CategoryEntity parent = categoryEntity.getParent();
        return (parent != null) ? new CategoryId(parent.getId()) : null;
    }
}

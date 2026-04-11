package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Maps a {@link CategoryEntity} to the domain model. Prefer using only when the entity is loaded in a
 * context where {@code parent} access is safe (e.g. same persistence session), to avoid accidental N+1 queries.
 */
@Component
public class CategoryAggregateMapper {

    public Category toCategory(CategoryEntity categoryEntity) {
        Objects.requireNonNull(categoryEntity, "categoryEntity must not be null.");
        Objects.requireNonNull(categoryEntity.getId(), "categoryEntity id must not be null.");

        CategoryId parentId = toParentId(categoryEntity);

        return Category.reconstitute(
                new CategoryId(categoryEntity.getId()),
                categoryEntity.getName(),
                parentId,
                toDomainStatus(categoryEntity.getStatus())
        );
    }

    private CategoryId toParentId(CategoryEntity categoryEntity) {
        CategoryEntity parent = categoryEntity.getParent();
        return (parent != null) ? new CategoryId(parent.getId()) : null;
    }

    private CategoryStatus toDomainStatus(CategoryStatusEntity entityStatus) {
        return CategoryStatus.valueOf(entityStatus.name());
    }
}

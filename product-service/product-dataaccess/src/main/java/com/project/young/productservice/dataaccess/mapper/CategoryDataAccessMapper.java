package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.domain.entity.Category;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class CategoryDataAccessMapper {

    public Category categoryEntityToCategory(CategoryEntity categoryEntity) {
        if (categoryEntity == null) {
            return null;
        }

        CategoryId parentId = (categoryEntity.getParent() != null)
                ? new CategoryId(categoryEntity.getParent().getId())
                : null;

        // Use the 'reconstitute' factory method instead of the builder
        return Category.reconstitute(
                new CategoryId(categoryEntity.getId()),
                categoryEntity.getName(),
                parentId,
                categoryEntity.getStatus()
        );
    }

    public CategoryEntity categoryToCategoryEntity(Category category, CategoryEntity parentEntity) {
        return CategoryEntity.builder()
                .id(category.getId() != null ? category.getId().getValue() : null)
                .name(category.getName())
                .status(category.getStatus())
                .parent(parentEntity)
                .children(new ArrayList<>())
                .build();
    }

    @Deprecated
    public CategoryEntity categoryToCategoryEntitySimple(Category category) {
        CategoryEntity entity = new CategoryEntity();
        if (category.getId() != null) {
            entity.setId(category.getId().getValue());
        }
        entity.setName(category.getName());
        entity.setStatus(category.getStatus());
        // parent는 Repository 계층에서 설정해야 함 (getReferenceById 사용)
        return entity;

    }

    public void updateEntityFromDomain(Category domainCategory, CategoryEntity categoryEntity) {
        categoryEntity.setName(domainCategory.getName());
        categoryEntity.setStatus(domainCategory.getStatus());
    }
}

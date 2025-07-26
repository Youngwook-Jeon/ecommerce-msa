package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.domain.entity.Category;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class CategoryDataAccessMapper {

    public Category categoryEntityToCategory(CategoryEntity categoryEntity) {
        return Category.builder()
                .categoryId(new CategoryId(categoryEntity.getId()))
                .name(categoryEntity.getName())
                .status(categoryEntity.getStatus())
                .parentId(categoryEntity.getParent() != null ?
                        new CategoryId(categoryEntity.getParent().getId()) : null)
                .build();
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

    public CategoryEntity categoryToCategoryEntitySimple(Category category) {
        CategoryEntity entity = new CategoryEntity();
        if (category.getId() != null) {
            entity.setId(category.getId().getValue());
        }
        entity.setName(category.getName());
        entity.setStatus(category.getStatus());

        if (category.getParentId().isPresent()) {
            CategoryEntity parentProxy = new CategoryEntity();
            parentProxy.setId(category.getParentId().get().getValue());
            entity.setParent(parentProxy);
        } else {
            entity.setParent(null);
        }

        return entity;
    }
}

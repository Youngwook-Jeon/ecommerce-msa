package com.project.young.productservice.application.mapper;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.application.dto.CreateCategoryCommand;
import com.project.young.productservice.application.dto.CreateCategoryResult;
import com.project.young.productservice.application.dto.DeleteCategoryResult;
import com.project.young.productservice.application.dto.UpdateCategoryResult;
import com.project.young.productservice.domain.entity.Category;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CategoryDataMapper {

    public CreateCategoryResult toCreateCategoryResult(Category category) {
        Objects.requireNonNull(category, "Category cannot be null");
        Objects.requireNonNull(category.getId(), "Category ID cannot be null");

        return CreateCategoryResult.builder()
                .id(category.getId().getValue())
                .name(category.getName())
                .build();
    }

    public UpdateCategoryResult toUpdateCategoryResult(Category category) {
        Objects.requireNonNull(category, "Category cannot be null");
        Objects.requireNonNull(category.getId(), "Category ID cannot be null");

        return UpdateCategoryResult.builder()
                .id(category.getId().getValue())
                .name(category.getName())
                .parentId(category.getParentId().isPresent() ? category.getParentId().get().getValue() : null)
                .status(category.getStatus())
                .build();
    }

    public DeleteCategoryResult toDeleteCategoryResult(Category category) {
        Objects.requireNonNull(category, "Category cannot be null");

        return DeleteCategoryResult.builder()
                .id(category.getId().getValue())
                .name(category.getName())
                .build();
    }

    public Category toCategory(CreateCategoryCommand command, CategoryId parentId) {
        Objects.requireNonNull(command, "CreateCategoryCommand cannot be null");

        return Category.builder()
                .name(command.getName())
                .parentId(parentId)
                .build();
    }
}

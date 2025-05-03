package com.project.young.productservice.application.mapper;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.application.dto.CreateCategoryCommand;
import com.project.young.productservice.application.dto.CreateCategoryResponse;
import com.project.young.productservice.domain.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryDataMapper {

    public CreateCategoryResponse toCreateCategoryResponse(Category category, String message) {
        return CreateCategoryResponse.builder()
                .name(category.getName())
                .message(message)
                .build();
    }

    public Category toCategory(CreateCategoryCommand command, CategoryId parentId) {
        return Category.builder()
                .name(command.getName())
                .parentId(parentId)
                .build();
    }
}

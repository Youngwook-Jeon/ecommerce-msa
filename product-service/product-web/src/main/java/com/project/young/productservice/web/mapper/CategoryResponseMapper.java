package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.dto.CreateCategoryResult;
import com.project.young.productservice.application.dto.DeleteCategoryResult;
import com.project.young.productservice.application.dto.UpdateCategoryResult;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import com.project.young.productservice.web.converter.CategoryStatusWebConverter;
import com.project.young.productservice.web.dto.CreateCategoryResponse;
import com.project.young.productservice.web.dto.DeleteCategoryResponse;
import com.project.young.productservice.web.dto.UpdateCategoryResponse;
import com.project.young.productservice.web.message.CategoryResponseMessageFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CategoryResponseMapper {

    private final CategoryResponseMessageFactory messageFactory;
    private final CategoryStatusWebConverter categoryStatusWebConverter;

    public CategoryResponseMapper(CategoryResponseMessageFactory messageFactory,
                                  CategoryStatusWebConverter categoryStatusWebConverter) {
        this.messageFactory = messageFactory;
        this.categoryStatusWebConverter = categoryStatusWebConverter;
    }

    public CreateCategoryResponse toCreateCategoryResponse(CreateCategoryResult result) {
        Objects.requireNonNull(result, "CreateCategoryResult cannot be null");

        return CreateCategoryResponse.builder()
                .id(result.id())
                .name(result.name())
                .message(messageFactory.categoryCreated())
                .build();
    }

    public UpdateCategoryResponse toUpdateCategoryResponse(UpdateCategoryResult result) {
        Objects.requireNonNull(result, "UpdateCategoryResult cannot be null");

        return UpdateCategoryResponse.builder()
                .id(result.id())
                .name(result.name())
                .parentId(result.parentId())
                .status(categoryStatusWebConverter.toStringValue(result.status()))
                .message(messageFactory.categoryUpdated())
                .build();
    }

    public DeleteCategoryResponse toDeleteCategoryResponse(DeleteCategoryResult result) {
        Objects.requireNonNull(result, "DeleteCategoryResult cannot be null");

        return DeleteCategoryResponse.builder()
                .id(result.id())
                .name(result.name())
                .message(messageFactory.categoryDeleted())
                .build();
    }

}

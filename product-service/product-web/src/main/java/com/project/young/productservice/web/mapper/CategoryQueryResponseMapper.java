package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.port.output.view.ReadCategoryView;
import com.project.young.productservice.web.converter.CategoryStatusWebConverter;
import com.project.young.productservice.web.dto.ReadCategoryNodeResponse;
import com.project.young.productservice.web.dto.ReadCategoryResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class CategoryQueryResponseMapper {

    private final CategoryStatusWebConverter categoryStatusWebConverter;

    public CategoryQueryResponseMapper(CategoryStatusWebConverter categoryStatusWebConverter) {
        this.categoryStatusWebConverter = categoryStatusWebConverter;
    }

    public ReadCategoryResponse toReadCategoryResponse(List<ReadCategoryView> readCategoryViews) {
        Objects.requireNonNull(readCategoryViews, "ReadCategoryViews is null");

        return new ReadCategoryResponse(
                readCategoryViews.stream().map(this::toReadCategoryNodeResponse).toList());
    }

    public ReadCategoryNodeResponse toReadCategoryNodeResponse(ReadCategoryView readCategoryView) {
        Objects.requireNonNull(readCategoryView, "ReadCategoryView is null");

        return ReadCategoryNodeResponse.builder()
                .id(readCategoryView.id())
                .name(readCategoryView.name())
                .parentId(readCategoryView.parentId())
                .status(categoryStatusWebConverter.toStringValue(readCategoryView.status()))
                .children(readCategoryView.children().stream().map(this::toReadCategoryNodeResponse).toList())
                .build();
    }

}

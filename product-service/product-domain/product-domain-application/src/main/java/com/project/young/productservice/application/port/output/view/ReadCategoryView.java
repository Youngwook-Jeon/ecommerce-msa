package com.project.young.productservice.application.port.output.view;

import com.project.young.productservice.domain.valueobject.CategoryStatus;
import lombok.Builder;

import java.util.List;

@Builder
public record ReadCategoryView(Long id, String name, Long parentId, CategoryStatus status, List<ReadCategoryView> children) {

    public ReadCategoryView {
        children = children == null ?
                List.of() :
                List.copyOf(children);
    }

    public ReadCategoryView(Long id, String name, Long parentId, CategoryStatus status) {
        this(id, name, parentId, status, List.of());
    }

}

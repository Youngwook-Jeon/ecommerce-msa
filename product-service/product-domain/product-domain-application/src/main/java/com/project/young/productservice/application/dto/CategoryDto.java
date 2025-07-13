package com.project.young.productservice.application.dto;

import java.util.List;

public record CategoryDto(Long id, String name, Long parentId, String status, List<CategoryDto> children) {

    public CategoryDto {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("Category ID must be positive");
        }

        if (name != null && name.isBlank()) {
            throw new IllegalArgumentException("Category name cannot be blank");
        }

        children = children == null ?
                List.of() :
                List.copyOf(children);
    }

    public CategoryDto(Long id, String name, Long parentId, String status) {
        this(id, name, parentId, status, List.of());
    }

}

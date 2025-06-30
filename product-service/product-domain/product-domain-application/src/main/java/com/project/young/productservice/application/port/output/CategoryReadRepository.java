package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.dto.CategoryDto;

import java.util.List;

public interface CategoryReadRepository {

    List<CategoryDto> findAllActiveCategoryHierarchy();
}

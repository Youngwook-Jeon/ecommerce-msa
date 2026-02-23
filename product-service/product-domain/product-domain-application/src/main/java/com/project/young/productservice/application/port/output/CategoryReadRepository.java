package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.port.output.view.ReadCategoryView;

import java.util.List;

public interface CategoryReadRepository {

    List<ReadCategoryView> findAllActiveCategoryHierarchy();

    List<ReadCategoryView> findAllCategoryHierarchy();
}

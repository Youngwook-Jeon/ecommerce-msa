package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.port.output.view.ReadCategoryView;

import java.util.List;

public interface CategoryReadRepository {

    List<ReadCategoryView> findAllActiveCategoryHierarchy();

    List<ReadCategoryView> findAllCategoryHierarchy();

    /**
     * @return true when an ACTIVE category exists with the given id (implemented in product-dataaccess).
     */
    boolean existsActiveById(long categoryId);
}

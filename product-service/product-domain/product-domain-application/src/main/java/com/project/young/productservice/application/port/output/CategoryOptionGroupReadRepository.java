package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.port.output.view.ReadCategoryOptionGroupView;

import java.util.List;

public interface CategoryOptionGroupReadRepository {
    /** Returns available groups from this category's explicit policies, without loading option values. */
    List<ReadCategoryOptionGroupView> findAvailableByCategoryId(Long categoryId);
}

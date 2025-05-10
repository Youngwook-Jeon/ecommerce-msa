package com.project.young.productservice.domain.service;

import com.project.young.common.domain.valueobject.CategoryId;

public interface CategoryDomainService {

    boolean isCategoryNameUnique(String name);

    boolean isParentDepthLessThanLimit(CategoryId parentId);

    boolean isCategoryNameUniqueForUpdate(String name, CategoryId categoryIdToExclude);
}

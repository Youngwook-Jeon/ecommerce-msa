package com.project.young.productservice.domain.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.domain.repository.CategoryRepository;

public class CategoryDomainServiceImpl implements CategoryDomainService {

    private final CategoryRepository categoryRepository;

    public CategoryDomainServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public boolean isCategoryNameUnique(String name) {
        return !categoryRepository.existsByName(name);
    }

    @Override
    public boolean isParentDepthLessThanLimit(CategoryId parentId) {
        return true;
    }
}

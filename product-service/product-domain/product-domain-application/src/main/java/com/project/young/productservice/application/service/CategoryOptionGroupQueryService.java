package com.project.young.productservice.application.service;

import com.project.young.productservice.application.port.output.AttributeCatalogPort;
import com.project.young.productservice.application.port.output.CategoryOptionGroupReadRepository;
import com.project.young.productservice.application.port.output.view.ReadCategoryOptionGroupView;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CategoryOptionGroupQueryService {
    private final AttributeCatalogPort catalog;
    private final CategoryOptionGroupReadRepository optionGroups;

    public CategoryOptionGroupQueryService(AttributeCatalogPort catalog, CategoryOptionGroupReadRepository optionGroups) {
        this.catalog = catalog;
        this.optionGroups = optionGroups;
    }

    public List<ReadCategoryOptionGroupView> getAvailableOptionGroups(Long categoryId) {
        if (categoryId == null || categoryId < 1) {
            throw new CategoryDomainException("Category ID must be positive");
        }
        catalog.findCategory(categoryId).filter(category -> !category.deleted())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + categoryId));
        return optionGroups.findAvailableByCategoryId(categoryId);
    }
}

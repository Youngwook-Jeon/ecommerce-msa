package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ProductSearchCategoryNameReader {
    private final CategoryJpaRepository categories;

    public ProductSearchCategoryNameReader(CategoryJpaRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Map<Long, String> read(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categories.findNamesByIdIn(ids).stream().collect(Collectors.toUnmodifiableMap(
                CategoryJpaRepository.CategoryNameView::getId, CategoryJpaRepository.CategoryNameView::getName));
    }
}

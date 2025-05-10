package com.project.young.productservice.domain.repository;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.domain.entity.Category;

import java.util.Optional;

public interface CategoryRepository {

    Category save(Category category);

    boolean existsByName(String name);

    boolean existsById(CategoryId categoryId);

    boolean existsByNameAndIdNot(String name, CategoryId categoryIdToExclude);

    Optional<Category> findById(CategoryId categoryId);
}

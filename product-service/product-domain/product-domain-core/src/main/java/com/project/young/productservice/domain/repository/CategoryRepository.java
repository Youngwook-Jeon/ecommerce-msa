package com.project.young.productservice.domain.repository;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.domain.entity.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    Category save(Category category);

    List<Category> saveAll(List<Category> categoriesToDelete);

    boolean existsByName(String name);

    boolean existsById(CategoryId categoryId);

    boolean existsByNameAndIdNot(String name, CategoryId categoryIdToExclude);

    Optional<Category> findById(CategoryId categoryId);

    List<Category> findAllSubTreeById(CategoryId categoryId);

}

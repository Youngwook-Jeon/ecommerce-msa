package com.project.young.productservice.domain.repository;

import com.project.young.productservice.domain.entity.Category;

public interface CategoryRepository {

    Category save(Category category);

    boolean existsByName(String name);

}

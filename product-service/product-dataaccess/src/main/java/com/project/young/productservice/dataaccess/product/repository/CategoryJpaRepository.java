package com.project.young.productservice.dataaccess.product.repository;

import com.project.young.productservice.dataaccess.product.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, Long> {

    boolean existsByName(String name);
}

package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductSearchIndexVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductSearchIndexVersionJpaRepository extends JpaRepository<ProductSearchIndexVersionEntity, UUID> {
}

package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductSearchIndexBuildJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductSearchIndexBuildJobJpaRepository extends JpaRepository<ProductSearchIndexBuildJobEntity, UUID> {
}

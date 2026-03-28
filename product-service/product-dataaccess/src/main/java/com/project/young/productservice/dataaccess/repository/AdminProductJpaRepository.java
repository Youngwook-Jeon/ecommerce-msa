package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AdminProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

    @Query("""
           SELECT DISTINCT p
           FROM ProductEntity p
           LEFT JOIN FETCH p.optionGroups og
           LEFT JOIN FETCH og.optionValues
           WHERE p.id = :productId
           """)
    Optional<ProductEntity> findAdminDetailWithOptionsById(@Param("productId") UUID productId);

    @Query("""
           SELECT DISTINCT p
           FROM ProductEntity p
           LEFT JOIN FETCH p.variants v
           LEFT JOIN FETCH v.selectedOptionValues
           WHERE p.id = :productId
           """)
    Optional<ProductEntity> findAdminDetailWithVariantsById(@Param("productId") UUID productId);
}
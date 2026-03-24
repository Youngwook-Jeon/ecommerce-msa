package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {
    @EntityGraph(attributePaths = {
            "optionGroups",
            "optionGroups.optionValues",
            "variants",
            "variants.selectedOptionValues"
    })
    @Query("""
           SELECT p
           FROM ProductEntity p
           WHERE p.id = :productId
           """)
    Optional<ProductEntity> findAggregateById(@Param("productId") UUID productId);

    @Query("""
           SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
           FROM ProductVariantEntity v
           WHERE v.sku = :sku
           """)
    boolean existsVariantSku(@Param("sku") String sku);

    @Query("""
           SELECT p
           FROM ProductEntity p
           JOIN p.category c
           WHERE p.status = :productStatus
            AND c.status = :categoryStatus
           """)
    List<ProductEntity> findAllVisible(@Param("productStatus") ProductStatusEntity productStatus,
                                         @Param("categoryStatus") CategoryStatusEntity categoryStatus);

    @Query("""
           SELECT p
           FROM ProductEntity p
           JOIN p.category c
           WHERE c.id = :categoryId
            AND p.status = :productStatus
            AND c.status = :categoryStatus
           """)
    List<ProductEntity> findVisibleByCategoryId(@Param("categoryId") Long categoryId,
                                                @Param("productStatus") ProductStatusEntity productStatus,
                                                @Param("categoryStatus") CategoryStatusEntity categoryStatus);

    @Query("""
           SELECT p
           FROM ProductEntity p
           LEFT JOIN p.category c
           WHERE p.id = :productId
            AND p.status = :productStatus
            AND (c IS NULL OR c.status = :categoryStatus)
           """)
    Optional<ProductEntity> findVisibleById(@Param("productId") UUID productId,
                                            @Param("productStatus") ProductStatusEntity productStatus,
                                            @Param("categoryStatus") CategoryStatusEntity categoryStatus);
}

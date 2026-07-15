package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantJpaRepository extends JpaRepository<ProductVariantEntity, UUID> {

    @Query("""
            SELECT DISTINCT v.id
            FROM ProductVariantEntity v
            JOIN v.selectedOptionValues sov
            WHERE v.product.id = :productId
            """)
    List<UUID> findAllIdsByProductId(@Param("productId") UUID productId);

    @Query("""
            SELECT DISTINCT v.id
            FROM ProductVariantEntity v
            JOIN v.selectedOptionValues sov
            WHERE sov.productOptionValueId = :productOptionValueId
            """)
    List<UUID> findAllIdsByProductOptionValueId(@Param("productOptionValueId") UUID productOptionValueId);

    @Query("""
            SELECT DISTINCT v
            FROM ProductVariantEntity v
            LEFT JOIN FETCH v.selectedOptionValues
            WHERE v.product.id = :productId
            """)
    List<ProductVariantEntity> findAllByProductIdWithSelectedOptionValues(@Param("productId") UUID productId);

    @Query("""
            SELECT v
            FROM ProductVariantEntity v
            LEFT JOIN FETCH v.selectedOptionValues
            JOIN FETCH v.product
            WHERE v.id = :variantId
            """)
    Optional<ProductVariantEntity> findByIdWithSelectedOptionValuesAndProduct(@Param("variantId") UUID variantId);

    @Modifying
    @Query("""
            UPDATE ProductVariantEntity v
            SET v.mainImageUrl = :mainImageUrl
            WHERE v.id = :variantId
            """)
    int updateMainImageUrl(@Param("variantId") UUID variantId, @Param("mainImageUrl") String mainImageUrl);

    @Modifying
    @Query("""
            UPDATE ProductVariantEntity v
            SET v.mainImageUrl = :mainImageUrl
            WHERE v.id IN :variantIds
            """)
    int updateMainImageUrlForIds(
            @Param("variantIds") Collection<UUID> variantIds,
            @Param("mainImageUrl") String mainImageUrl
    );

    @Query("""
            SELECT DISTINCT v
            FROM ProductVariantEntity v
            JOIN FETCH v.product p
            LEFT JOIN p.category c
            LEFT JOIN FETCH v.selectedOptionValues
            WHERE v.id IN :variantIds
              AND p.status NOT IN :excludedProductStatuses
              AND (c IS NULL OR c.status <> :excludedCategoryStatus)
            """)
    List<ProductVariantEntity> findStorefrontCartVariantsByIdIn(
            @Param("variantIds") Collection<UUID> variantIds,
            @Param("excludedProductStatuses") List<ProductStatusEntity> excludedProductStatuses,
            @Param("excludedCategoryStatus") CategoryStatusEntity excludedCategoryStatus
    );

    @Query("""
            SELECT v
            FROM ProductVariantEntity v
            JOIN FETCH v.product
            WHERE v.id IN :variantIds
            ORDER BY v.id ASC
            """)
    List<ProductVariantEntity> findAllByIdInWithProductOrdered(@Param("variantIds") Collection<UUID> variantIds);
}

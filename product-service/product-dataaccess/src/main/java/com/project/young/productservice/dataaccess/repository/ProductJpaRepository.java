package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {
    @Query("""
            SELECT p FROM ProductEntity p JOIN FETCH p.category c
            WHERE p.status = :productStatus AND c.status = :categoryStatus
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<ProductEntity> findIndexVerificationSample(@Param("productStatus") ProductStatusEntity productStatus,
            @Param("categoryStatus") CategoryStatusEntity categoryStatus, Pageable pageable);

    @Query("SELECT p FROM ProductEntity p LEFT JOIN FETCH p.category ORDER BY p.id ASC")
    List<ProductEntity> findFirstSearchIndexBatch(Pageable pageable);

    @Query("SELECT p FROM ProductEntity p LEFT JOIN FETCH p.category WHERE p.id > :afterId ORDER BY p.id ASC")
    List<ProductEntity> findNextSearchIndexBatch(@Param("afterId") UUID afterId, Pageable pageable);

    @Query("SELECT p FROM ProductEntity p LEFT JOIN FETCH p.category WHERE p.id = :id")
    Optional<ProductEntity> findSearchDocumentById(@Param("id") UUID id);

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

    @EntityGraph(attributePaths = {
            "optionGroups",
            "optionGroups.optionValues",
            "variants",
            "variants.selectedOptionValues"
    })
    @Query("""
           SELECT p
           FROM ProductEntity p
           LEFT JOIN p.category c
           WHERE p.id = :productId
            AND p.status = :productStatus
            AND (c IS NULL OR c.status = :categoryStatus)
           """)
    Optional<ProductEntity> findVisibleDetailById(@Param("productId") UUID productId,
                                                  @Param("productStatus") ProductStatusEntity productStatus,
                                                  @Param("categoryStatus") CategoryStatusEntity categoryStatus);

    @Query("""
           SELECT DISTINCT p
           FROM ProductEntity p
           LEFT JOIN p.category c
           LEFT JOIN FETCH p.optionGroups og
           LEFT JOIN FETCH og.optionValues
           WHERE p.id = :productId
            AND p.status = :productStatus
            AND (c IS NULL OR c.status = :categoryStatus)
           """)
    Optional<ProductEntity> findVisibleDetailWithOptionsById(@Param("productId") UUID productId,
                                                             @Param("productStatus") ProductStatusEntity productStatus,
                                                             @Param("categoryStatus") CategoryStatusEntity categoryStatus);

    @Query("""
           SELECT DISTINCT p
           FROM ProductEntity p
           LEFT JOIN p.category c
           LEFT JOIN FETCH p.variants v
           LEFT JOIN FETCH v.selectedOptionValues
           WHERE p.id = :productId
            AND p.status = :productStatus
            AND (c IS NULL OR c.status = :categoryStatus)
           """)
    Optional<ProductEntity> findVisibleDetailWithVariantsById(@Param("productId") UUID productId,
                                                              @Param("productStatus") ProductStatusEntity productStatus,
                                                              @Param("categoryStatus") CategoryStatusEntity categoryStatus);

    @Query("""
           SELECT DISTINCT p
           FROM ProductEntity p
           LEFT JOIN p.category c
           LEFT JOIN FETCH p.optionGroups og
           LEFT JOIN FETCH og.optionValues
           WHERE p.id = :productId
            AND p.status NOT IN :excludedStatuses
            AND (c IS NULL OR c.status <> :excludedCategoryStatus)
           """)
    Optional<ProductEntity> findStorefrontDetailWithOptionsById(@Param("productId") UUID productId,
                                                                 @Param("excludedStatuses") List<ProductStatusEntity> excludedStatuses,
                                                                 @Param("excludedCategoryStatus") CategoryStatusEntity excludedCategoryStatus);

    @Query("""
           SELECT DISTINCT p
           FROM ProductEntity p
           LEFT JOIN p.category c
           LEFT JOIN FETCH p.variants v
           LEFT JOIN FETCH v.selectedOptionValues
           WHERE p.id = :productId
            AND p.status NOT IN :excludedStatuses
            AND (c IS NULL OR c.status <> :excludedCategoryStatus)
           """)
    Optional<ProductEntity> findStorefrontDetailWithVariantsById(@Param("productId") UUID productId,
                                                                  @Param("excludedStatuses") List<ProductStatusEntity> excludedStatuses,
                                                                  @Param("excludedCategoryStatus") CategoryStatusEntity excludedCategoryStatus);

    @Query("""
           SELECT DISTINCT p
           FROM ProductEntity p
           LEFT JOIN p.category c
           LEFT JOIN FETCH p.optionGroups og
           LEFT JOIN FETCH og.optionValues
           WHERE p.id IN :productIds
            AND p.status NOT IN :excludedStatuses
            AND (c IS NULL OR c.status <> :excludedCategoryStatus)
           """)
    List<ProductEntity> findStorefrontOptionsByIdIn(@Param("productIds") Collection<UUID> productIds,
                                                    @Param("excludedStatuses") List<ProductStatusEntity> excludedStatuses,
                                                    @Param("excludedCategoryStatus") CategoryStatusEntity excludedCategoryStatus);
}

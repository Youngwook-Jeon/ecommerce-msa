package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AdminProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

    @Query("""
       SELECT p
       FROM ProductEntity p
       LEFT JOIN p.category c
       WHERE (:hasStatus = FALSE OR p.status = :status)
         AND (:hasBrand = FALSE OR p.brand = :brand)
         AND (
                :hasKeyword = FALSE
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
             )
         AND (
                :hasCategoryId = FALSE
                OR c.id = :categoryId
             )
         AND (
                :includeOrphans = TRUE
                OR c IS NOT NULL
             )
       """)
    Page<ProductEntity> searchAdminProducts(@Param("hasCategoryId") boolean hasCategoryId,
                                            @Param("categoryId") Long categoryId,
                                            @Param("includeOrphans") boolean includeOrphans,
                                            @Param("hasStatus") boolean hasStatus,
                                            @Param("status") ProductStatusEntity status,
                                            @Param("hasBrand") boolean hasBrand,
                                            @Param("brand") String brand,
                                            @Param("hasKeyword") boolean hasKeyword,
                                            @Param("keyword") String keyword,
                                            Pageable pageable);
}
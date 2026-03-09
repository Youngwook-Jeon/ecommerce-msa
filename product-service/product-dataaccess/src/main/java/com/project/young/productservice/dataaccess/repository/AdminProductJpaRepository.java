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
       WHERE COALESCE(:status, p.status) = p.status
         AND COALESCE(:brand, p.brand) = p.brand
         AND (
                COALESCE(:keyword, '') = ''
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
             )
         AND (
                :categoryId IS NULL
                OR c.id = :categoryId
             )
         AND (
                (:includeOrphans = TRUE AND (c IS NULL OR c.status IS NOT NULL))
                OR (:includeOrphans = FALSE AND c IS NOT NULL)
             )
       """)
    Page<ProductEntity> searchAdminProducts(@Param("categoryId") Long categoryId,
                                            @Param("includeOrphans") boolean includeOrphans,
                                            @Param("status") ProductStatusEntity status,
                                            @Param("brand") String brand,
                                            @Param("keyword") String keyword,
                                            Pageable pageable);
}


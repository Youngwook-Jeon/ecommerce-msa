package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductImageEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductImageRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductImageJpaRepository extends JpaRepository<ProductImageEntity, UUID> {

    List<ProductImageEntity> findByProduct_IdAndStatusOrderBySortOrderAsc(
            UUID productId,
            OptionStatusEntity status
    );

    Optional<ProductImageEntity> findByIdAndProduct_Id(UUID id, UUID productId);

    Optional<ProductImageEntity> findByStorageKeyAndProduct_Id(String storageKey, UUID productId);

    @Modifying
    @Query("""
            UPDATE ProductImageEntity img
            SET img.role = :galleryRole
            WHERE img.product.id = :productId
              AND img.role = :mainRole
              AND img.status = :activeStatus
            """)
    int demoteActiveMainsToGallery(
            @Param("productId") UUID productId,
            @Param("galleryRole") ProductImageRoleEntity galleryRole,
            @Param("mainRole") ProductImageRoleEntity mainRole,
            @Param("activeStatus") OptionStatusEntity activeStatus
    );

    long countByProduct_IdAndStatusAndRole(
            UUID productId,
            OptionStatusEntity status,
            ProductImageRoleEntity role
    );

    @Modifying
    @Query("""
            UPDATE ProductImageEntity img
            SET img.status = :deleted
            WHERE img.id = :id AND img.product.id = :productId AND img.status = :active
            """)
    int softDelete(
            @Param("id") UUID id,
            @Param("productId") UUID productId,
            @Param("active") OptionStatusEntity active,
            @Param("deleted") OptionStatusEntity deleted
    );

    @Modifying
    @Query("""
            UPDATE ProductImageEntity img
            SET img.role = :newRole
            WHERE img.id = :id AND img.product.id = :productId AND img.status = :active
            """)
    int updateRole(
            @Param("id") UUID id,
            @Param("productId") UUID productId,
            @Param("newRole") ProductImageRoleEntity newRole,
            @Param("active") OptionStatusEntity active
    );
}

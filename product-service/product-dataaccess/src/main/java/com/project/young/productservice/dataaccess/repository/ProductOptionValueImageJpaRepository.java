package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductOptionValueImageEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductImageRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductOptionValueImageJpaRepository extends JpaRepository<ProductOptionValueImageEntity, UUID> {

    List<ProductOptionValueImageEntity> findByProductOptionValue_IdAndStatusOrderBySortOrderAsc(
            UUID productOptionValueId,
            OptionStatusEntity status
    );

    List<ProductOptionValueImageEntity> findByProductOptionValue_IdInAndStatusOrderBySortOrderAsc(
            Collection<UUID> productOptionValueIds,
            OptionStatusEntity status
    );

    Optional<ProductOptionValueImageEntity> findByIdAndProductOptionValue_Id(UUID id, UUID productOptionValueId);

    Optional<ProductOptionValueImageEntity> findByStorageKeyAndProductOptionValue_Id(
            String storageKey,
            UUID productOptionValueId
    );

    @Modifying
    @Query("""
            UPDATE ProductOptionValueImageEntity img
            SET img.role = :galleryRole
            WHERE img.productOptionValue.id = :productOptionValueId
              AND img.role = :mainRole
              AND img.status = :activeStatus
            """)
    int demoteActiveMainsToGallery(
            @Param("productOptionValueId") UUID productOptionValueId,
            @Param("galleryRole") ProductImageRoleEntity galleryRole,
            @Param("mainRole") ProductImageRoleEntity mainRole,
            @Param("activeStatus") OptionStatusEntity activeStatus
    );

    @Modifying
    @Query("""
            UPDATE ProductOptionValueImageEntity img
            SET img.status = :deleted
            WHERE img.id = :id
              AND img.productOptionValue.id = :productOptionValueId
              AND img.status = :active
            """)
    int softDelete(
            @Param("id") UUID id,
            @Param("productOptionValueId") UUID productOptionValueId,
            @Param("active") OptionStatusEntity active,
            @Param("deleted") OptionStatusEntity deleted
    );

    @Modifying
    @Query("""
            UPDATE ProductOptionValueImageEntity img
            SET img.role = :newRole
            WHERE img.id = :id
              AND img.productOptionValue.id = :productOptionValueId
              AND img.status = :active
            """)
    int updateRole(
            @Param("id") UUID id,
            @Param("productOptionValueId") UUID productOptionValueId,
            @Param("newRole") ProductImageRoleEntity newRole,
            @Param("active") OptionStatusEntity active
    );

    @Modifying
    @Query("""
            UPDATE ProductOptionValueImageEntity img
            SET img.sortOrder = :sortOrder
            WHERE img.id = :id
              AND img.productOptionValue.id = :productOptionValueId
              AND img.status = :active
            """)
    int updateSortOrder(
            @Param("id") UUID id,
            @Param("productOptionValueId") UUID productOptionValueId,
            @Param("sortOrder") int sortOrder,
            @Param("active") OptionStatusEntity active
    );
}

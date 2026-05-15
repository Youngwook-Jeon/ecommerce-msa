package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductOptionGroupEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductOptionGroupJpaRepository extends JpaRepository<ProductOptionGroupEntity, UUID> {

    Optional<ProductOptionGroupEntity> findByIdAndProduct_Id(UUID id, UUID productId);

    @Query("""
            SELECT pog
            FROM ProductOptionGroupEntity pog
            WHERE pog.product.id = :productId
              AND pog.drivesVariantImages = true
              AND pog.status = :activeStatus
            """)
    Optional<ProductOptionGroupEntity> findActiveVisualGroupByProductId(
            @Param("productId") UUID productId,
            @Param("activeStatus") OptionStatusEntity activeStatus
    );

    @Modifying
    @Query("""
            UPDATE ProductOptionGroupEntity pog
            SET pog.drivesVariantImages = false
            WHERE pog.product.id = :productId
              AND pog.status = :activeStatus
              AND pog.drivesVariantImages = true
            """)
    int clearVisualFlagsForProduct(
            @Param("productId") UUID productId,
            @Param("activeStatus") OptionStatusEntity activeStatus
    );

    @Modifying
    @Query("""
            UPDATE ProductOptionGroupEntity pog
            SET pog.drivesVariantImages = :drivesVariantImages
            WHERE pog.id = :productOptionGroupId
              AND pog.product.id = :productId
              AND pog.status = :activeStatus
            """)
    int updateDrivesVariantImages(
            @Param("productId") UUID productId,
            @Param("productOptionGroupId") UUID productOptionGroupId,
            @Param("drivesVariantImages") boolean drivesVariantImages,
            @Param("activeStatus") OptionStatusEntity activeStatus
    );
}

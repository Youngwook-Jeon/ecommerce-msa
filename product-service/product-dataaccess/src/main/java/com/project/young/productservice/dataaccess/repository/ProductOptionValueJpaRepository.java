package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductOptionValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductOptionValueJpaRepository extends JpaRepository<ProductOptionValueEntity, UUID> {

    @Query("""
            SELECT pov.productOptionGroup.product.id
            FROM ProductOptionValueEntity pov
            WHERE pov.id = :productOptionValueId
            """)
    Optional<UUID> findProductIdByProductOptionValueId(@Param("productOptionValueId") UUID productOptionValueId);

    @Query("""
            SELECT pov.productOptionGroup.id
            FROM ProductOptionValueEntity pov
            WHERE pov.id = :productOptionValueId
            """)
    Optional<UUID> findProductOptionGroupIdByProductOptionValueId(
            @Param("productOptionValueId") UUID productOptionValueId
    );

    boolean existsByIdAndProductOptionGroup_Product_Id(UUID id, UUID productId);

    @Query("""
            SELECT pov.id, pov.productOptionGroup.id
            FROM ProductOptionValueEntity pov
            WHERE pov.productOptionGroup.product.id = :productId
            """)
    List<Object[]> findPovIdAndGroupIdByProductId(@Param("productId") UUID productId);

    @Query("""
            SELECT pov.id, pov.productOptionGroup.id
            FROM ProductOptionValueEntity pov
            WHERE pov.id IN :povIds
            """)
    List<Object[]> findPovIdAndGroupIdByPovIds(@Param("povIds") Collection<UUID> povIds);
}

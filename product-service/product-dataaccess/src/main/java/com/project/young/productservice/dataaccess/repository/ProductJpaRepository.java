package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    List<ProductEntity> findByCategoryId(Long categoryId);

    List<ProductEntity> findByCategoryIdIn(List<Long> categoryIds);

    List<ProductEntity> findByBrandId(UUID brandId);

    List<ProductEntity> findByBrandIdIn(List<UUID> brandIds);

    long countByCategoryIdAndStatus(Long categoryId, String status);

    long countByBrandIdAndStatus(UUID brandId, String status);

    @Modifying
    @Query("UPDATE ProductEntity p SET p.status = :status WHERE p.id IN :ids")
    int updateStatusByIdIn(@Param("status") String status, @Param("ids") List<UUID> ids);

    @Query("SELECT p FROM ProductEntity p WHERE p.status = :status")
    List<ProductEntity> findByStatus(@Param("status") String status);

    @Query("SELECT p FROM ProductEntity p WHERE p.categoryId IS NULL")
    List<ProductEntity> findProductsWithoutCategory();

    @Query("SELECT p FROM ProductEntity p WHERE p.brandId IS NULL")
    List<ProductEntity> findProductsWithoutBrand();
}
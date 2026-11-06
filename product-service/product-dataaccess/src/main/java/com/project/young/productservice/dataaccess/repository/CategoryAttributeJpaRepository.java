package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.CategoryAttributeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Set;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryAttributeJpaRepository extends JpaRepository<CategoryAttributeEntity, UUID> {
    boolean existsByAttributeId(UUID attributeId);

    @Query("SELECT policy.attributeId FROM CategoryAttributeEntity policy WHERE policy.categoryId = :categoryId")
    Set<UUID> findAttributeIdsByCategoryId(@Param("categoryId") Long categoryId);

    List<CategoryAttributeEntity> findByCategoryId(Long categoryId);

    Optional<CategoryAttributeEntity> findByCategoryIdAndAttributeId(Long categoryId, UUID attributeId);
}

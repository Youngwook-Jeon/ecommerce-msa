package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.CategoryAttributeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryAttributeJpaRepository extends JpaRepository<CategoryAttributeEntity, UUID> {
    boolean existsByAttributeId(UUID attributeId);

    List<CategoryAttributeEntity> findByCategoryId(Long categoryId);

    Optional<CategoryAttributeEntity> findByCategoryIdAndAttributeId(Long categoryId, UUID attributeId);
}

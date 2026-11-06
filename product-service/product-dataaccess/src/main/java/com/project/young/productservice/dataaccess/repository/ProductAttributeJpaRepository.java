package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductAttributeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductAttributeJpaRepository extends JpaRepository<ProductAttributeEntity, UUID> {
    boolean existsByAttributeId(UUID attributeId);

    boolean existsByAttributeValueId(UUID attributeValueId);
}
